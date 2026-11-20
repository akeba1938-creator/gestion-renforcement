package com.ecole.saas.attendance.api;

import java.util.UUID;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

final class AttendanceApiSupport {

    private AttendanceApiSupport() {
    }

    static UUID extractActorId(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("Identifiant d'utilisateur authentifié introuvable.");
        }
        return UUID.fromString(authentication.getName());
    }
}
