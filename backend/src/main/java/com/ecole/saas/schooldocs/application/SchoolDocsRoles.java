package com.ecole.saas.schooldocs.application;

final class SchoolDocsRoles {

    static final String TEMPLATE_WRITE_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'SECRETARY')";

    static final String TEMPLATE_READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'SECRETARY', 'CENSOR', 'CASHIER', 'TEACHER', 'TUTOR')";

    static final String DOCUMENT_ISSUE_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'SECRETARY', 'CENSOR')";

    static final String DOCUMENT_REVOKE_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER')";

    static final String DOCUMENT_READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'SECRETARY', 'CENSOR', 'CASHIER', 'TEACHER', 'TUTOR', 'PARENT', 'STUDENT')";

    static final String SUMMARY_READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'SECRETARY', 'CENSOR')";

    private SchoolDocsRoles() {
    }
}
