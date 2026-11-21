package com.ecole.saas.student;

import java.util.UUID;

/**
 * Vue immuable des coordonnées de contact d'un élève exposée aux modules
 * applicatifs (ex. {@code communication}) sans exposer les entités internes.
 */
public record StudentContactSummary(
        UUID id,
        String matricule,
        UUID userId,
        String firstName,
        String lastName,
        String phone,
        String email,
        boolean active) {
}
