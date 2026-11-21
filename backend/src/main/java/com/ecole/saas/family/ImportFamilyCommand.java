package com.ecole.saas.family;

/**
 * Commande publique immuable d'import (création ou mise à jour contrôlée) d'une famille.
 */
public record ImportFamilyCommand(
        String code,
        String name,
        String primaryPhone,
        String email,
        String address,
        String city,
        String notes) {
}
