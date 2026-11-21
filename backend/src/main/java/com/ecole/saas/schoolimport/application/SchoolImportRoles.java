package com.ecole.saas.schoolimport.application;

final class SchoolImportRoles {

    static final String READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'SECRETARY', 'CENSOR', 'TEACHER', 'TUTOR')";
    static final String WRITE_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'SECRETARY', 'CENSOR', 'TEACHER', 'TUTOR')";
    static final String SUMMARY_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'SECRETARY', 'CENSOR')";

    private SchoolImportRoles() {
    }
}
