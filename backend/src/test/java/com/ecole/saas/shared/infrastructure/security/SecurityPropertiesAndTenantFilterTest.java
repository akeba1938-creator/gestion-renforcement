package com.ecole.saas.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SecurityPropertiesAndTenantFilterTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void securityPropertiesShouldRejectMissingShortOrInvalidBase64SecretsAndMaskToString() {
        String shortSecret = Base64.getEncoder().encodeToString("trop-court".getBytes(StandardCharsets.UTF_8));
        String validSecret = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

        SecurityProperties missingJwt = new SecurityProperties(
                new SecurityProperties.Jwt("", "ecole-saas"),
                new SecurityProperties.Mfa(validSecret),
                null,
                null);
        assertThatThrownBy(missingJwt::decodedJwtSecretBytes).isInstanceOf(IllegalStateException.class);

        SecurityProperties shortJwt = new SecurityProperties(
                new SecurityProperties.Jwt(shortSecret, "ecole-saas"),
                new SecurityProperties.Mfa(validSecret),
                null,
                null);
        assertThatThrownBy(shortJwt::decodedJwtSecretBytes).isInstanceOf(IllegalStateException.class);

        SecurityProperties badBase64Mfa = new SecurityProperties(
                new SecurityProperties.Jwt(validSecret, "ecole-saas"),
                new SecurityProperties.Mfa("!!!not-base64!!!"),
                null,
                null);
        assertThatThrownBy(badBase64Mfa::decodedMfaEncryptionKeyBytes).isInstanceOf(IllegalStateException.class);

        SecurityProperties valid = new SecurityProperties(
                new SecurityProperties.Jwt(validSecret, "ecole-saas"),
                new SecurityProperties.Mfa(validSecret),
                null,
                null);
        assertThat(valid.decodedJwtSecretBytes()).hasSize(32);
        assertThat(valid.decodedMfaEncryptionKeyBytes()).hasSize(32);
        assertThat(valid.toString()).doesNotContain(validSecret);
    }

    @Test
    void tenantContextFilterShouldUseOnlyAuthenticatedJwtAndIgnoreClientHeadersOrParams() throws Exception {
        UUID jwtTenantId = UUID.randomUUID();
        UUID spoofedTenantId = UUID.randomUUID();
        TenantContextFilter filter = new TenantContextFilter();

        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(900),
                Map.of("alg", "HS256"),
                Map.of(
                        "sub", UUID.randomUUID().toString(),
                        JwtClaims.CLAIM_SUBJECT_TYPE, JwtClaims.SUBJECT_TYPE_USER,
                        JwtClaims.CLAIM_TENANT_ID, jwtTenantId.toString(),
                        JwtClaims.CLAIM_TOKEN_TYPE, JwtClaims.TOKEN_TYPE_ACCESS));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(JwtClaims.AUTHORITY_ACCESS))));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("X-Tenant-Id", spoofedTenantId.toString());
        request.addParameter("tenantId", spoofedTenantId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<TenantId> observedTenant = new AtomicReference<>();
        FilterChain chain = (req, res) -> observedTenant.set(TenantContext.getCurrent().orElse(null));

        filter.doFilter(request, response, chain);

        assertThat(observedTenant.get()).isEqualTo(TenantId.of(jwtTenantId));
        assertThat(TenantContext.getCurrent()).isEmpty();
    }

    @Test
    void tenantContextFilterShouldLeaveTenantEmptyForPlatformAdminAndUnauthenticatedAndClearOnError() throws Exception {
        UUID spoofedTenantId = UUID.randomUUID();
        TenantContextFilter filter = new TenantContextFilter();

        Jwt platformJwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(900),
                Map.of("alg", "HS256"),
                Map.of(
                        "sub", UUID.randomUUID().toString(),
                        JwtClaims.CLAIM_SUBJECT_TYPE, JwtClaims.SUBJECT_TYPE_PLATFORM_ADMIN,
                        JwtClaims.CLAIM_TOKEN_TYPE, JwtClaims.TOKEN_TYPE_ACCESS));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(platformJwt, List.of(new SimpleGrantedAuthority(JwtClaims.AUTHORITY_ACCESS))));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("X-Tenant-Id", spoofedTenantId.toString());

        AtomicReference<Boolean> tenantWasPresent = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                tenantWasPresent.set(TenantContext.getCurrent().isPresent()));
        assertThat(tenantWasPresent.get()).isFalse();

        // Même si le filtre aval lève une exception, TenantContext est nettoyé
        UUID jwtTenantId = UUID.randomUUID();
        Jwt userJwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(900),
                Map.of("alg", "HS256"),
                Map.of(
                        "sub", UUID.randomUUID().toString(),
                        JwtClaims.CLAIM_SUBJECT_TYPE, JwtClaims.SUBJECT_TYPE_USER,
                        JwtClaims.CLAIM_TENANT_ID, jwtTenantId.toString(),
                        JwtClaims.CLAIM_TOKEN_TYPE, JwtClaims.TOKEN_TYPE_ACCESS));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(userJwt, List.of(new SimpleGrantedAuthority(JwtClaims.AUTHORITY_ACCESS))));

        assertThatThrownBy(() -> filter.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/test"),
                new MockHttpServletResponse(),
                (req, res) -> {
                    assertThat(TenantContext.getCurrent()).contains(TenantId.of(jwtTenantId));
                    throw new IllegalStateException("erreur simulée");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(TenantContext.getCurrent()).isEmpty();
    }
}
