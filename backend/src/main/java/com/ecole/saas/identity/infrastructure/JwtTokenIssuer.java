package com.ecole.saas.identity.infrastructure;

import com.ecole.saas.identity.domain.AccountRef;
import com.ecole.saas.identity.domain.TokenIssuer;
import com.ecole.saas.identity.domain.TokenLifetimes;
import com.ecole.saas.shared.infrastructure.security.JwtClaims;
import com.ecole.saas.shared.infrastructure.security.SecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Émetteur de JWT HS256 : jetons d'accès (15 min) et jetons intermédiaires MFA (5 min).
 */
@Component
public class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Clock clock;

    public JwtTokenIssuer(JwtEncoder jwtEncoder, SecurityProperties securityProperties, Clock clock) {
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "L'encodeur JWT est obligatoire.");
        this.issuer = Objects.requireNonNull(securityProperties, "La configuration de sécurité est obligatoire.")
                .jwt()
                .issuer();
        this.clock = Objects.requireNonNull(clock, "L'horloge est obligatoire.");
    }

    @Override
    public IssuedToken issueAccessToken(AccountRef account, Set<String> roles) {
        Objects.requireNonNull(account, "Le compte est obligatoire.");
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Un jeton d'accès doit porter au moins un rôle.");
        }
        List<String> sortedRoles = roles.stream().sorted().toList();
        return encode(account, JwtClaims.TOKEN_TYPE_ACCESS, TokenLifetimes.ACCESS_TOKEN, sortedRoles);
    }

    @Override
    public IssuedToken issueMfaPendingToken(AccountRef account) {
        Objects.requireNonNull(account, "Le compte est obligatoire.");
        return encode(account, JwtClaims.TOKEN_TYPE_MFA_PENDING, TokenLifetimes.MFA_PENDING_TOKEN, null);
    }

    private IssuedToken encode(AccountRef account, String tokenType, Duration lifetime, List<String> roles) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(lifetime);

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .id(UUID.randomUUID().toString())
                .subject(account.accountId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(JwtClaims.CLAIM_TOKEN_TYPE, tokenType)
                .claim(JwtClaims.CLAIM_SUBJECT_TYPE, account.type().name());

        if (account.tenantId() != null) {
            claims.claim(JwtClaims.CLAIM_TENANT_ID, account.tenantId().toString());
        }
        if (roles != null) {
            claims.claim(JwtClaims.CLAIM_ROLES, roles);
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(tokenValue, lifetime.toSeconds());
    }
}
