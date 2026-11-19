package com.ecole.saas.identity.domain;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

public final class MfaPolicy {

    public static final Set<RoleCode> SENSITIVE_TENANT_ROLES =
            EnumSet.of(RoleCode.PROMOTER, RoleCode.DIRECTOR, RoleCode.CENTER_MANAGER);

    private MfaPolicy() {
    }

    public static boolean isRequiredForTenantUser(Collection<RoleCode> roles, boolean mfaAlreadyEnabled) {
        return mfaAlreadyEnabled || roles.stream().anyMatch(SENSITIVE_TENANT_ROLES::contains);
    }

    public static boolean isRequiredForPlatformAdmin() {
        return true;
    }
}
