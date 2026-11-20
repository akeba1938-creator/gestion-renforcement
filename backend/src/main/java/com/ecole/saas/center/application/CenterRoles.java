package com.ecole.saas.center.application;

final class CenterRoles {

    static final String READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'CENSOR', 'SECRETARY', 'CASHIER', 'TEACHER', 'TUTOR')";
    static final String STAFF_READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'CENSOR', 'SECRETARY', 'CASHIER')";
    static final String PACK_CATALOG_WRITE_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER')";
    static final String STUDENT_PACK_WRITE_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'SECRETARY')";
    static final String GROUP_MANAGE_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'CENSOR')";
    static final String ENROLLMENT_WRITE_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'SECRETARY')";
    static final String SESSION_CONDUCT_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'CENSOR', 'TEACHER', 'TUTOR')";

    private CenterRoles() {
    }
}
