package com.ecole.saas.audit.application;

import com.ecole.saas.audit.AuditActorType;
import com.ecole.saas.shared.infrastructure.security.JwtClaims;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Résout de manière sûre l'acteur authentifié (depuis le JWT vérifié) et l'identifiant
 * de corrélation de requête (depuis le MDC), sans jamais faire confiance aux en-têtes client.
 */
final class AuditActorResolver {

    private static final String MDC_REQUEST_ID = "requestId";

    private AuditActorResolver() {
    }

    static ResolvedActor resolve(UUID explicitActorId, AuditActorType explicitActorType, String explicitActorRoles) {
        String correlationId = MDC.get(MDC_REQUEST_ID);

        UUID actorId = explicitActorId;
        AuditActorType actorType = explicitActorType;
        String actorRoles = explicitActorRoles;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth && auth.isAuthenticated()) {
            Jwt jwt = jwtAuth.getToken();
            if (actorId == null) {
                actorId = tryParseUuid(jwt.getSubject());
            }
            if (actorType == null) {
                String subjectType = jwt.getClaimAsString(JwtClaims.CLAIM_SUBJECT_TYPE);
                if (JwtClaims.SUBJECT_TYPE_PLATFORM_ADMIN.equals(subjectType)) {
                    actorType = AuditActorType.PLATFORM_ADMIN;
                } else if (JwtClaims.SUBJECT_TYPE_USER.equals(subjectType)) {
                    actorType = AuditActorType.USER;
                }
            }
            if (actorRoles == null) {
                List<String> roles = jwt.getClaimAsStringList(JwtClaims.CLAIM_ROLES);
                if (roles != null && !roles.isEmpty()) {
                    actorRoles = roles.stream()
                            .filter(r -> r != null && !r.isBlank())
                            .sorted()
                            .collect(Collectors.joining(","));
                }
            }
        }

        if (actorType == null) {
            actorType = actorId != null ? AuditActorType.USER : AuditActorType.SYSTEM;
        }
        return new ResolvedActor(actorType, actorId, actorRoles, correlationId);
    }

    private static UUID tryParseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    record ResolvedActor(
            AuditActorType actorType,
            UUID actorId,
            String actorRoles,
            String correlationId) {
    }
}
