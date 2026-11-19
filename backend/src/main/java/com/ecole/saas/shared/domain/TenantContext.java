package com.ecole.saas.shared.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class TenantContext {

    private static final ThreadLocal<TenantId> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<TenantId> getCurrent() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public static TenantId requireCurrent() {
        TenantId tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("Aucun contexte d'établissement actif.");
        }
        return tenantId;
    }

    public static void set(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "L'identifiant d'établissement ne peut pas être nul.");
        CURRENT_TENANT.set(tenantId);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    public static <T> T executeWithTenant(TenantId tenantId, Supplier<T> action) {
        Objects.requireNonNull(tenantId, "L'identifiant d'établissement ne peut pas être nul.");
        Objects.requireNonNull(action, "L'action à exécuter ne peut pas être nulle.");
        TenantId previous = CURRENT_TENANT.get();
        CURRENT_TENANT.set(tenantId);
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    public static void runWithTenant(TenantId tenantId, Runnable action) {
        Objects.requireNonNull(action, "L'action à exécuter ne peut pas être nulle.");
        executeWithTenant(tenantId, () -> {
            action.run();
            return null;
        });
    }

    private static void restore(TenantId previous) {
        if (previous == null) {
            CURRENT_TENANT.remove();
        } else {
            CURRENT_TENANT.set(previous);
        }
    }
}
