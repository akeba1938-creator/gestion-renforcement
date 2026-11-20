package com.ecole.saas.finance.api;

import com.ecole.saas.finance.domain.PaymentMethod;
import com.ecole.saas.finance.domain.Receipt;
import java.time.Instant;
import java.util.UUID;

public record ReceiptResponse(
        UUID id,
        UUID paymentId,
        UUID invoiceId,
        UUID studentId,
        String receiptNumber,
        String verificationToken,
        long amountXof,
        String currency,
        PaymentMethod paymentMethod,
        Instant issuedAt,
        UUID issuedBy) {

    public static ReceiptResponse from(Receipt receipt) {
        if (receipt == null) {
            return null;
        }
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getPaymentId(),
                receipt.getInvoiceId(),
                receipt.getStudentId(),
                receipt.getReceiptNumber(),
                receipt.getVerificationToken(),
                receipt.getAmountXof(),
                receipt.getCurrency(),
                receipt.getPaymentMethod(),
                receipt.getIssuedAt(),
                receipt.getIssuedBy());
    }
}
