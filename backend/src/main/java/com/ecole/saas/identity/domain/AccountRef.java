package com.ecole.saas.identity.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Référence d'un compte authentifié. Le tenant n'existe que pour les utilisateurs d'établissement
 * et provient toujours de la base de données, jamais du client.
 */
public record AccountRef(SubjectType type, UUID accountId, UUID tenantId) {

    public AccountRef {
        Objects.requireNonNull(type, "Le type de compte est obligatoire.");
        Objects.requireNonNull(accountId, "L'identifiant de compte est obligatoire.");
        if (type == SubjectType.USER && tenantId == null) {
            throw new IllegalArgumentException("Un utilisateur d'établissement doit avoir un tenant.");
        }
        if (type == SubjectType.PLATFORM_ADMIN && tenantId != null) {
            throw new IllegalArgumentException("Un administrateur SaaS n'appartient à aucun établissement.");
        }
    }

    public static AccountRef user(UUID userId, UUID tenantId) {
        return new AccountRef(SubjectType.USER, userId, tenantId);
    }

    public static AccountRef platformAdmin(UUID adminId) {
        return new AccountRef(SubjectType.PLATFORM_ADMIN, adminId, null);
    }

    public boolean isPlatformAdmin() {
        return type == SubjectType.PLATFORM_ADMIN;
    }
}
