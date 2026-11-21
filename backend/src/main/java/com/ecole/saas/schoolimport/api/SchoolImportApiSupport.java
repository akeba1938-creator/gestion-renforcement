package com.ecole.saas.schoolimport.api;

import com.ecole.saas.schoolimport.domain.SchoolImportValidationException;
import java.util.UUID;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

final class SchoolImportApiSupport {

    private SchoolImportApiSupport() {
    }

    static UUID extractActorId(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getToken() == null
                || authentication.getToken().getSubject() == null) {
            throw new SchoolImportValidationException("Impossible d'identifier l'utilisateur authentifié.");
        }
        try {
            return UUID.fromString(authentication.getToken().getSubject());
        } catch (IllegalArgumentException ex) {
            throw new SchoolImportValidationException("Identifiant d'acteur JWT invalide.");
        }
    }

    static String resolveIdempotencyKey(String headerValue, String bodyValue) {
        String h = headerValue != null && !headerValue.isBlank() ? headerValue.trim() : null;
        String b = bodyValue != null && !bodyValue.isBlank() ? bodyValue.trim() : null;
        if (h != null && b != null && !h.equals(b)) {
            throw new SchoolImportValidationException(
                    "L'en-tête Idempotency-Key et le champ idempotencyKey doivent être identiques.");
        }
        return h != null ? h : b;
    }
}
