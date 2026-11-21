package com.ecole.saas.communication.application;

final class CommunicationRoles {

    static final String TEMPLATE_WRITE_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'SECRETARY')";

    static final String TEMPLATE_READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'SECRETARY', 'CASHIER', 'CENSOR', 'TEACHER', 'TUTOR')";

    static final String PREFERENCE_ADMIN_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'SECRETARY')";

    static final String CAMPAIGN_SEND_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'SECRETARY', 'CASHIER', 'CENSOR', 'TEACHER', 'TUTOR')";

    static final String CAMPAIGN_READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'SECRETARY', 'CASHIER', 'CENSOR', 'TEACHER', 'TUTOR')";

    static final String DELIVERY_ADMIN_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'SECRETARY')";

    static final String INBOX_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'CENSOR', 'SECRETARY', 'CASHIER', 'TEACHER', 'TUTOR', 'PARENT', 'STUDENT')";

    private CommunicationRoles() {
    }
}
