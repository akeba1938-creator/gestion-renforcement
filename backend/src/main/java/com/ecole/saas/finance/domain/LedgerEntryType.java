package com.ecole.saas.finance.domain;

public enum LedgerEntryType {
    CHARGE(LedgerDirection.DEBIT),
    CHARGE_REVERSAL(LedgerDirection.CREDIT),
    DISCOUNT_CREDIT(LedgerDirection.CREDIT),
    SCHOLARSHIP_CREDIT(LedgerDirection.CREDIT),
    PAYMENT_CREDIT(LedgerDirection.CREDIT),
    PAYMENT_REVERSAL(LedgerDirection.DEBIT);

    private final LedgerDirection defaultDirection;

    LedgerEntryType(LedgerDirection defaultDirection) {
        this.defaultDirection = defaultDirection;
    }

    public LedgerDirection defaultDirection() {
        return defaultDirection;
    }
}
