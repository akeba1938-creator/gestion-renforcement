package com.ecole.saas.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecole.saas.identity.domain.AccountRef;
import com.ecole.saas.identity.domain.TokenIssuer;
import com.ecole.saas.identity.domain.TokenLifetimes;
import com.ecole.saas.shared.infrastructure.security.JwtAuthenticationConverter;
import com.ecole.saas.shared.infrastructure.security.JwtClaims;
import com.ecole.saas.shared.infrastructure.security.JwtCryptoConfig;
import com.ecole.saas.shared.infrastructure.security.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class AuthCryptoInfrastructureTest {

    private static final String TEST_JWT_SECRET =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String TEST_MFA_KEY =
            Base64.getEncoder().encodeToString("test-mfa-aes256-key-32bytes-only".getBytes(StandardCharsets.UTF_8));

    private static SecurityProperties testProperties() {
        return new SecurityProperties(
                new SecurityProperties.Jwt(TEST_JWT_SECRET, "ecole-saas"),
                new SecurityProperties.Mfa(TEST_MFA_KEY),
                new SecurityProperties.Cookie("ecole_refresh_token", true, "Strict", "/api/v1/auth"),
                new SecurityProperties.Csrf("XSRF-TOKEN", "X-XSRF-TOKEN"));
    }

    @Test
    void argon2PasswordHasherShouldUseArgon2idWithUniqueSalts() {
        Argon2PasswordHasher hasher = new Argon2PasswordHasher();

        String hash1 = hasher.hash("MotDePasseSolide2026!");
        String hash2 = hasher.hash("MotDePasseSolide2026!");

        assertThat(hash1).startsWith("$argon2id$");
        assertThat(hash2).startsWith("$argon2id$");
        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(hasher.matches("MotDePasseSolide2026!", hash1)).isTrue();
        assertThat(hasher.matches("MauvaisMotDePasse!", hash1)).isFalse();
        assertThat(hasher.matches(null, hash1)).isFalse();
        assertThat(hasher.matches("MotDePasseSolide2026!", null)).isFalse();
    }

    @Test
    void aesGcmSecretCipherShouldEncryptWithRandomIvAndDetectTampering() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(testProperties());

        String encrypted1 = cipher.encrypt("JBSWY3DPEHPK3PXP");
        String encrypted2 = cipher.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(encrypted1).startsWith("v1:");
        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(encrypted1).doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(cipher.decrypt(encrypted1)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(cipher.decrypt(encrypted2)).isEqualTo("JBSWY3DPEHPK3PXP");

        // Altération du texte chiffré -> détection GCM
        byte[] rawPayload = Base64.getDecoder().decode(encrypted1.substring(3));
        rawPayload[rawPayload.length - 1] ^= 0x01;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(rawPayload);
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);

        // Autre clé -> échec de déchiffrement
        byte[] otherKey = "other-aes256-key-32bytes-length!".getBytes(StandardCharsets.UTF_8);
        AesGcmSecretCipher otherCipher = AesGcmSecretCipher.withKey(otherKey);
        assertThatThrownBy(() -> otherCipher.decrypt(encrypted1)).isInstanceOf(IllegalStateException.class);

        // Clé de mauvaise taille -> rejetée
        assertThatThrownBy(() -> AesGcmSecretCipher.withKey(new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jwtTokenIssuerShouldEmitAccess15MinAndMfaPending5MinWithStrictClaims() {
        SecurityProperties properties = testProperties();
        JwtCryptoConfig cryptoConfig = new JwtCryptoConfig();
        SecretKey key = cryptoConfig.jwtSecretKey(properties);
        JwtEncoder encoder = cryptoConfig.jwtEncoder(key);
        JwtDecoder decoder = cryptoConfig.jwtDecoder(key, properties);
        Instant now = Instant.now();
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);

        JwtTokenIssuer issuer = new JwtTokenIssuer(encoder, properties, fixedClock);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        TokenIssuer.IssuedToken access = issuer.issueAccessToken(
                AccountRef.user(userId, tenantId),
                Set.of("DIRECTOR", "TEACHER"));

        assertThat(access.expiresInSeconds()).isEqualTo(TokenLifetimes.ACCESS_TOKEN.toSeconds());
        Jwt decodedAccess = decoder.decode(access.value());
        assertThat(decodedAccess.getSubject()).isEqualTo(userId.toString());
        assertThat(decodedAccess.getClaimAsString(JwtClaims.CLAIM_TENANT_ID)).isEqualTo(tenantId.toString());
        assertThat(decodedAccess.getClaimAsString(JwtClaims.CLAIM_TOKEN_TYPE)).isEqualTo(JwtClaims.TOKEN_TYPE_ACCESS);
        assertThat(decodedAccess.getClaimAsString(JwtClaims.CLAIM_SUBJECT_TYPE)).isEqualTo(JwtClaims.SUBJECT_TYPE_USER);
        assertThat(decodedAccess.getClaimAsStringList(JwtClaims.CLAIM_ROLES)).containsExactly("DIRECTOR", "TEACHER");
        assertThat(decodedAccess.getExpiresAt().getEpochSecond() - decodedAccess.getIssuedAt().getEpochSecond())
                .isEqualTo(900L);

        AbstractAuthenticationToken authAccess = converter.convert(decodedAccess);
        assertThat(authAccess.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(JwtClaims.AUTHORITY_ACCESS, "ROLE_DIRECTOR", "ROLE_TEACHER");

        // Jeton MFA en attente (5 min, aucun rôle métier)
        TokenIssuer.IssuedToken mfaPending = issuer.issueMfaPendingToken(AccountRef.user(userId, tenantId));
        assertThat(mfaPending.expiresInSeconds()).isEqualTo(TokenLifetimes.MFA_PENDING_TOKEN.toSeconds());
        Jwt decodedMfa = decoder.decode(mfaPending.value());
        assertThat(decodedMfa.getClaimAsString(JwtClaims.CLAIM_TOKEN_TYPE)).isEqualTo(JwtClaims.TOKEN_TYPE_MFA_PENDING);
        assertThat(decodedMfa.getClaimAsStringList(JwtClaims.CLAIM_ROLES)).isNull();
        assertThat(decodedMfa.getExpiresAt().getEpochSecond() - decodedMfa.getIssuedAt().getEpochSecond())
                .isEqualTo(300L);

        AbstractAuthenticationToken authMfa = converter.convert(decodedMfa);
        assertThat(authMfa.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(JwtClaims.AUTHORITY_MFA_PENDING);

        // Jeton administrateur plateforme sans tenant_id
        UUID adminId = UUID.randomUUID();
        TokenIssuer.IssuedToken adminAccess = issuer.issueAccessToken(
                AccountRef.platformAdmin(adminId),
                Set.of("ADMIN_SAAS"));
        Jwt decodedAdmin = decoder.decode(adminAccess.value());
        assertThat(decodedAdmin.getClaimAsString(JwtClaims.CLAIM_TENANT_ID)).isNull();
        assertThat(decodedAdmin.getClaimAsString(JwtClaims.CLAIM_SUBJECT_TYPE))
                .isEqualTo(JwtClaims.SUBJECT_TYPE_PLATFORM_ADMIN);
    }

    @Test
    void jwtAuthenticationConverterShouldRejectInvalidClaimCombinations() {
        SecurityProperties properties = testProperties();
        JwtCryptoConfig cryptoConfig = new JwtCryptoConfig();
        SecretKey key = cryptoConfig.jwtSecretKey(properties);
        JwtEncoder encoder = cryptoConfig.jwtEncoder(key);
        JwtDecoder decoder = cryptoConfig.jwtDecoder(key, properties);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        Instant now = Instant.now();

        // USER sans tenant_id -> rejeté
        Jwt userWithoutTenant = decoder.decode(encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer("ecole-saas")
                        .subject(UUID.randomUUID().toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(300))
                        .claim(JwtClaims.CLAIM_SUBJECT_TYPE, JwtClaims.SUBJECT_TYPE_USER)
                        .claim(JwtClaims.CLAIM_TOKEN_TYPE, JwtClaims.TOKEN_TYPE_ACCESS)
                        .claim(JwtClaims.CLAIM_ROLES, List.of("DIRECTOR"))
                        .build())).getTokenValue());
        assertThatThrownBy(() -> converter.convert(userWithoutTenant))
                .isInstanceOf(InvalidBearerTokenException.class);

        // PLATFORM_ADMIN avec tenant_id -> rejeté
        Jwt adminWithTenant = decoder.decode(encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer("ecole-saas")
                        .subject(UUID.randomUUID().toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(300))
                        .claim(JwtClaims.CLAIM_SUBJECT_TYPE, JwtClaims.SUBJECT_TYPE_PLATFORM_ADMIN)
                        .claim(JwtClaims.CLAIM_TENANT_ID, UUID.randomUUID().toString())
                        .claim(JwtClaims.CLAIM_TOKEN_TYPE, JwtClaims.TOKEN_TYPE_ACCESS)
                        .claim(JwtClaims.CLAIM_ROLES, List.of("ADMIN_SAAS"))
                        .build())).getTokenValue());
        assertThatThrownBy(() -> converter.convert(adminWithTenant))
                .isInstanceOf(InvalidBearerTokenException.class);

        // mfa_pending avec roles -> rejeté
        Jwt mfaWithRoles = decoder.decode(encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer("ecole-saas")
                        .subject(UUID.randomUUID().toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(300))
                        .claim(JwtClaims.CLAIM_SUBJECT_TYPE, JwtClaims.SUBJECT_TYPE_USER)
                        .claim(JwtClaims.CLAIM_TENANT_ID, UUID.randomUUID().toString())
                        .claim(JwtClaims.CLAIM_TOKEN_TYPE, JwtClaims.TOKEN_TYPE_MFA_PENDING)
                        .claim(JwtClaims.CLAIM_ROLES, List.of("DIRECTOR"))
                        .build())).getTokenValue());
        assertThatThrownBy(() -> converter.convert(mfaWithRoles))
                .isInstanceOf(InvalidBearerTokenException.class);
    }
}
