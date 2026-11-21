package com.ecole.saas.audit.application;

final class AuditRoles {

    static final String TENANT_AUDIT_READERS = "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER')";
    static final String PLATFORM_ADMIN = "hasRole('ADMIN_SAAS')";

    private AuditRoles() {
    }
}
