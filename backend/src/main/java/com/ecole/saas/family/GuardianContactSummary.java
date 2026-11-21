package com.ecole.saas.family;

import java.util.UUID;

/**
 * Vue immuable des coordonnées de contact d'un parent/tuteur exposée aux modules
 * applicatifs (ex. {@code communication}) sans exposer les entités internes.
 */
public record GuardianContactSummary(
        UUID id,
        UUID familyId,
        UUID userId,
        String firstName,
        String lastName,
        String phone,
        String secondaryPhone,
        String email,
        String preferredLanguage,
        boolean active) {
}
