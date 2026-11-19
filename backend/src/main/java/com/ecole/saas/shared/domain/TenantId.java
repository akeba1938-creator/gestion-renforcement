package com.ecole.saas.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "L'identifiant d'établissement ne peut pas être nul.");
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }
}
