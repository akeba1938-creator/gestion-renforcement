package com.ecole.saas.reporting.application;

/**
 * Constantes SpEL {@code @PreAuthorize} du module {@code reporting}.
 * Le rôle {@code ADMIN_SAAS} est explicitement exclu de toutes les routes tenant {@code /api/v1/reporting/**}.
 */
public final class ReportingRoles {

    public static final String EXECUTIVE_ROLES =
            "hasAnyRole('PROMOTER','DIRECTOR','CENTER_MANAGER')";

    public static final String ACADEMIC_ATTENDANCE_ROLES =
            "hasAnyRole('PROMOTER','DIRECTOR','CENTER_MANAGER','CENSOR','SECRETARY','TEACHER','TUTOR')";

    public static final String FINANCIAL_ROLES =
            "hasAnyRole('PROMOTER','DIRECTOR','CENTER_MANAGER','CASHIER')";

    public static final String OPERATIONS_ROLES =
            "hasAnyRole('PROMOTER','DIRECTOR','CENTER_MANAGER','CENSOR','SECRETARY')";

    public static final String STUDENT_OVERVIEW_ROLES =
            "hasAnyRole('PROMOTER','DIRECTOR','CENTER_MANAGER','CENSOR','SECRETARY','CASHIER','TEACHER','TUTOR','PARENT','STUDENT')";

    public static final String ANY_REPORTING_ROLE = STUDENT_OVERVIEW_ROLES;

    public static final String SNAPSHOT_ARCHIVE_ROLES =
            "hasAnyRole('PROMOTER','DIRECTOR','CENTER_MANAGER')";

    private ReportingRoles() {
    }
}
