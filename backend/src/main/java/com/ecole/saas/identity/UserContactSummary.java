package com.ecole.saas.identity;

import java.util.Set;
import java.util.UUID;

/**
 * Vue immuable des coordonnées de contact d'un compte utilisateur de l'établissement
 * exposée aux modules applicatifs (ex. {@code communication}).
 */
public record UserContactSummary(
        UUID id,
        String email,
        String phone,
        String firstName,
        String lastName,
        boolean active,
        Set<String> roles) {
}
