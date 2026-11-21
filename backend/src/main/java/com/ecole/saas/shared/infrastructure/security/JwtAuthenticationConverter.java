package com.ecole.saas.shared.infrastructure.security;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Convertit un JWT vérifié en {@link JwtAuthenticationToken} tout en validant strictement
 * les invariants de sécurité (type de jeton, type de compte, cohérence du tenant et rôles).
 */
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        requireUuid(jwt.getSubject(), "Identifiant de sujet JWT invalide.");

        String subjectType = jwt.getClaimAsString(JwtClaims.CLAIM_SUBJECT_TYPE);
        String tenantId = jwt.getClaimAsString(JwtClaims.CLAIM_TENANT_ID);

        if (JwtClaims.SUBJECT_TYPE_USER.equals(subjectType)) {
            requireUuid(tenantId, "Identifiant d'établissement JWT manquant ou invalide.");
        } else if (JwtClaims.SUBJECT_TYPE_PLATFORM_ADMIN.equals(subjectType)) {
            if (tenantId != null && !tenantId.isBlank()) {
                throw new InvalidBearerTokenException("Jeton d'authentification invalide.");
            }
        } else {
            throw new InvalidBearerTokenException("Jeton d'authentification invalide.");
        }

        String tokenType = jwt.getClaimAsString(JwtClaims.CLAIM_TOKEN_TYPE);
        List<String> roles = jwt.getClaimAsStringList(JwtClaims.CLAIM_ROLES);
        List<GrantedAuthority> authorities = new ArrayList<>();

        if (JwtClaims.TOKEN_TYPE_ACCESS.equals(tokenType)) {
            if (roles == null || roles.isEmpty()) {
                throw new InvalidBearerTokenException("Jeton d'authentification invalide.");
            }
            authorities.add(new SimpleGrantedAuthority(JwtClaims.AUTHORITY_ACCESS));
            for (String role : roles) {
                if (role == null || role.isBlank()) {
                    throw new InvalidBearerTokenException("Jeton d'authentification invalide.");
                }
                if (JwtClaims.SUBJECT_TYPE_USER.equals(subjectType) && "ADMIN_SAAS".equals(role)) {
                    throw new InvalidBearerTokenException("Jeton d'authentification invalide.");
                }
                if (JwtClaims.SUBJECT_TYPE_PLATFORM_ADMIN.equals(subjectType) && !"ADMIN_SAAS".equals(role)) {
                    throw new InvalidBearerTokenException("Jeton d'authentification invalide.");
                }
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        } else if (JwtClaims.TOKEN_TYPE_MFA_PENDING.equals(tokenType)) {
            if (roles != null && !roles.isEmpty()) {
                throw new InvalidBearerTokenException("Jeton d'authentification invalide.");
            }
            authorities.add(new SimpleGrantedAuthority(JwtClaims.AUTHORITY_MFA_PENDING));
        } else {
            throw new InvalidBearerTokenException("Jeton d'authentification invalide.");
        }

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private static void requireUuid(String raw, String reason) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidBearerTokenException(reason);
        }
        try {
            UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException(reason, e);
        }
    }
}
