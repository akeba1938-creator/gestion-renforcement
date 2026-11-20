package com.ecole.saas.finance.api;

import com.ecole.saas.finance.application.PaymentService;
import com.ecole.saas.finance.domain.PaymentMethod;
import java.time.Instant;
import java.util.UUID;

public record ReceiptVerificationResponse(
        UUID receiptId,
        String receiptNumber,
        long amountXof,
        String currency,
        PaymentMethod paymentMethod,
        Instant issuedAt,
        String invoiceNumber,
        String studentMatricule,
        boolean reversed,
        Instant reversedAt) {

    public static ReceiptVerificationResponse from(PaymentService.ReceiptVerificationView view) {
        return new ReceiptVerificationResponse(
                view.receiptId(),
                view.receiptNumber(),
                view.amountXof(),
                view.currency(),
                view.paymentMethod(),
                view.issuedAt(),
                view.invoiceNumber(),
                view.studentMatricule(),
                view.reversed(),
                view.reversedAt());
    }
}
