package com.ecole.saas.attendance.application;

final class AttendanceRoles {

    static final String READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'CENSOR', 'SECRETARY', 'TEACHER', 'TUTOR')";
    static final String ROLL_CALL_WRITE_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'CENSOR', 'TEACHER', 'TUTOR')";
    static final String JUSTIFY_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'CENSOR', 'SECRETARY')";
    static final String ADMIN_MANAGE_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'CENSOR')";

    private AttendanceRoles() {
    }
}
