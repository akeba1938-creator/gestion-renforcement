package com.ecole.saas.communication;

/**
 * Catégories fonctionnelles de messages et notifications.
 */
public enum MessageCategory {
    GENERAL,
    ABSENCE_ALERT,
    PAYMENT_RECEIPT,
    PAYMENT_DUE_REMINDER,
    REPORT_CARD_PUBLISHED,
    CENTER_SESSION,
    SUMMONS,
    ANNOUNCEMENT,
    CUSTOM;

    public boolean isFinancial() {
        return this == PAYMENT_RECEIPT || this == PAYMENT_DUE_REMINDER;
    }

    public boolean isMandatoryTransactional() {
        return this == ABSENCE_ALERT
                || this == PAYMENT_RECEIPT
                || this == PAYMENT_DUE_REMINDER
                || this == REPORT_CARD_PUBLISHED
                || this == SUMMONS;
    }
}
