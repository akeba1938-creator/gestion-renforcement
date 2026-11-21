package com.ecole.saas.finance;

/**
 * Ventilation des encaissements et contre-passations par mode de paiement, en entiers FCFA ({@code long}).
 */
public record PaymentMethodBreakdown(
        String paymentMethod,
        long collectedAmountXof,
        long paymentCount,
        long reversedAmountXof,
        long reversalCount,
        long netCollectedAmountXof) {
}
