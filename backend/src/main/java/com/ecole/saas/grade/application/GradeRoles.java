package com.ecole.saas.grade.application;

final class GradeRoles {

    static final String READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'CENSOR', 'SECRETARY', 'TEACHER', 'TUTOR')";
    static final String EVALUATION_WRITE_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'CENSOR', 'TEACHER', 'TUTOR')";
    static final String EVALUATION_VALIDATE_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'CENSOR')";
    static final String EVALUATION_LOCK_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER')";
    static final String REPORT_CARD_COMPUTE_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'CENSOR')";
    static final String REPORT_CARD_VALIDATE_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'CENSOR')";
    static final String REPORT_CARD_LOCK_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER')";
    static final String APPRECIATION_WRITE_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'CENSOR', 'TEACHER', 'TUTOR')";

    private GradeRoles() {
    }
}
