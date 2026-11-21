package com.ecole.saas.family;

import java.util.UUID;

/**
 * Commande publique immuable d'import (création ou mise à jour contrôlée) d'un parent/tuteur légal.
 */
public record ImportGuardianCommand(
        UUID familyId,
        String firstName,
        String lastName,
        String gender,
        String phone,
        String secondaryPhone,
        String email,
        String profession,
        String address,
        String nationalIdNumber,
        String preferredLanguage) {
}
