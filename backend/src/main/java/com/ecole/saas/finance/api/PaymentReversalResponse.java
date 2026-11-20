package com.ecole.saas.finance.api;

import com.ecole.saas.finance.domain.PaymentMethod;
import com.ecole.saas.finance.domain.PaymentReversal;
import java.time.Instant;
import java.util.UUID;

public record PaymentReversalResponse(
        UUID id,
        UUID originalPaymentId,
        UUID reversalRequestId,
        UUID invoiceId,
        UUID studentId,
        long amountXof,
        PaymentMethod paymentMethod,
        String reason,
        UUID approvedBy,
        Instant reversedAt) {

    public static PaymentReversalResponse from(PaymentReversal reversal) {
        if (reversal == null) {
            return null;
        }
        return new PaymentReversalResponse(
                reversal.getId(),
                reversal.getOriginalPaymentId(),
                reversal.getReversalRequestId(),
                reversal.getInvoiceId(),
                reversal.getStudentId(),
                reversal.getAmountXof(),
                reversal.getPaymentMethod(),
                reversal.getReason(),
                reversal.getApprovedBy(),
                reversal.getReversedAt());
    }
}
