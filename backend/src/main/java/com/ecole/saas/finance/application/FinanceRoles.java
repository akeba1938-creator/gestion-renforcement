package com.ecole.saas.finance.application;

final class FinanceRoles {

    static final String READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'SECRETARY', 'CASHIER')";
    static final String CATALOG_WRITE_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER')";
    static final String INVOICE_WRITE_ROLES =
            "hasAnyRole('DIRECTOR', 'CENTER_MANAGER', 'SECRETARY', 'CASHIER')";
    static final String APPROVAL_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER')";
    static final String ADJUSTMENT_REQUEST_ROLES =
            "hasAnyRole('CASHIER', 'SECRETARY', 'DIRECTOR', 'CENTER_MANAGER')";
    static final String PAYMENT_RECORD_ROLES =
            "hasAnyRole('CASHIER', 'CENTER_MANAGER')";
    static final String REVERSAL_REQUEST_ROLES =
            "hasAnyRole('CASHIER', 'SECRETARY', 'DIRECTOR', 'CENTER_MANAGER')";
    static final String CASH_CLOSING_READ_ROLES =
            "hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER', 'CASHIER')";
    static final String CASH_CLOSING_SUBMIT_ROLES =
            "hasAnyRole('CASHIER', 'CENTER_MANAGER')";

    private FinanceRoles() {
    }
}
