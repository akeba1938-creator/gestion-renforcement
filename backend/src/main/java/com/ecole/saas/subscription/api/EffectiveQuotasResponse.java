package com.ecole.saas.subscription.api;

import com.ecole.saas.subscription.EffectiveQuotas;

public record EffectiveQuotasResponse(
        int baseStudents,
        int extraStudents,
        int maxStudents,
        int baseUsers,
        int extraUsers,
        int maxUsers,
        int baseStorageMb,
        int extraStorageMb,
        int maxStorageMb) {

    public static EffectiveQuotasResponse from(EffectiveQuotas quotas) {
        return new EffectiveQuotasResponse(
                quotas.baseStudents(),
                quotas.extraStudents(),
                quotas.maxStudents(),
                quotas.baseUsers(),
                quotas.extraUsers(),
                quotas.maxUsers(),
                quotas.baseStorageMb(),
                quotas.extraStorageMb(),
                quotas.maxStorageMb());
    }
}
