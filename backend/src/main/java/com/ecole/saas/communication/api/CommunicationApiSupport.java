package com.ecole.saas.communication.api;

import com.ecole.saas.communication.domain.CommunicationValidationException;
import java.util.UUID;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

final class CommunicationApiSupport {

    private CommunicationApiSupport() {
    }

    static UUID extractActorId(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getToken() == null) {
            throw new CommunicationValidationException("Jeton d'authentification manquant.");
        }
        try {
            return UUID.fromString(authentication.getToken().getSubject());
        } catch (IllegalArgumentException ex) {
            throw new CommunicationValidationException("Identifiant utilisateur JWT invalide.");
        }
    }

    static String resolveIdempotencyKey(String headerKey, String bodyKey) {
        boolean hasHeader = headerKey != null && !headerKey.isBlank();
        boolean hasBody = bodyKey != null && !bodyKey.isBlank();
        if (hasHeader && hasBody && !headerKey.trim().equals(bodyKey.trim())) {
            throw new CommunicationValidationException(
                    "L'en-tête Idempotency-Key et le champ idempotencyKey du corps doivent être identiques.");
        }
        if (hasHeader) {
            return headerKey.trim();
        }
        if (hasBody) {
            return bodyKey.trim();
        }
        throw new CommunicationValidationException(
                "La clé d'idempotence est obligatoire (en-tête Idempotency-Key ou champ idempotencyKey).");
    }
}
