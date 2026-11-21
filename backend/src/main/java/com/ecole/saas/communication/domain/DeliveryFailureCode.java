package com.ecole.saas.communication.domain;

/**
 * Code structuré expliquant pourquoi une remise n'a pas pu aboutir.
 */
public enum DeliveryFailureCode {
    CHANNEL_OPTED_OUT(false),
    MARKETING_OPTED_OUT(false),
    MISSING_CONTACT_ADDRESS(false),
    RECIPIENT_INACTIVE(false),
    GATEWAY_REJECTED(false),
    GATEWAY_ERROR(true);

    private final boolean retryable;

    DeliveryFailureCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
