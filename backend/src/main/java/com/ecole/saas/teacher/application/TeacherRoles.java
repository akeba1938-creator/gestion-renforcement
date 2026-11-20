package com.ecole.saas.teacher.application;

final class TeacherRoles {

    static final String BACKOFFICE_READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'CENSOR', 'SECRETARY')";
    static final String WRITE_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'CENSOR')";
    static final String SELF_ROLES =
            "hasAnyRole('TEACHER', 'TUTOR')";

    private TeacherRoles() {
    }
}
