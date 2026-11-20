package com.ecole.saas.finance.domain;

public enum PaymentMethod {
    CASH(false),
    CHECK(true),
    BANK_TRANSFER(true),
    MOBILE_MONEY_WAVE(true),
    MOBILE_MONEY_OM(true),
    MOBILE_MONEY_FREE(true),
    OTHER(false);

    private final boolean externalReferenceRequired;

    PaymentMethod(boolean externalReferenceRequired) {
        this.externalReferenceRequired = externalReferenceRequired;
    }

    public boolean isExternalReferenceRequired() {
        return externalReferenceRequired;
    }

    public boolean isCash() {
        return this == CASH;
    }
}
