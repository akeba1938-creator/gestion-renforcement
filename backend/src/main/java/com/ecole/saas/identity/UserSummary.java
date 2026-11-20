package com.ecole.saas.identity;

import java.util.Set;
import java.util.UUID;

/**
 * Vue immuable d'un compte utilisateur de l'établissement courant exposée aux autres modules
 * sans fuite des entités internes ni des informations sensibles du module {@code identity}.
 */
public record UserSummary(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String status,
        boolean active,
        Set<String> roles) {
}
