package com.ecole.saas.subscription;

import java.util.Objects;

/**
 * Quotas effectifs d'un abonnement (quotas de base du forfait + extensions négociées).
 */
public record EffectiveQuotas(
        int baseStudents,
        int extraStudents,
        int maxStudents,
        int baseUsers,
        int extraUsers,
        int maxUsers,
        int baseStorageMb,
        int extraStorageMb,
        int maxStorageMb) {

    public EffectiveQuotas {
        if (baseStudents <= 0 || baseUsers <= 0 || baseStorageMb <= 0) {
            throw new IllegalArgumentException("Les quotas de base doivent être strictement positifs.");
        }
        if (extraStudents < 0 || extraUsers < 0 || extraStorageMb < 0) {
            throw new IllegalArgumentException("Les extensions de quotas doivent être positives ou nulles.");
        }
        if (maxStudents != Math.addExact(baseStudents, extraStudents)
                || maxUsers != Math.addExact(baseUsers, extraUsers)
                || maxStorageMb != Math.addExact(baseStorageMb, extraStorageMb)) {
            throw new IllegalArgumentException("Les quotas effectifs doivent correspondre à la somme base + extension.");
        }
    }

    public static EffectiveQuotas of(
            int baseStudents,
            int extraStudents,
            int baseUsers,
            int extraUsers,
            int baseStorageMb,
            int extraStorageMb) {
        return new EffectiveQuotas(
                baseStudents,
                extraStudents,
                Math.addExact(baseStudents, extraStudents),
                baseUsers,
                extraUsers,
                Math.addExact(baseUsers, extraUsers),
                baseStorageMb,
                extraStorageMb,
                Math.addExact(baseStorageMb, extraStorageMb));
    }

    public int limitFor(QuotaResource resource) {
        Objects.requireNonNull(resource, "La ressource de quota est obligatoire.");
        return switch (resource) {
            case STUDENTS -> maxStudents;
            case USERS -> maxUsers;
            case STORAGE_MB -> maxStorageMb;
        };
    }
}
