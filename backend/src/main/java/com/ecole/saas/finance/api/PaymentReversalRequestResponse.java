package com.ecole.saas.finance.api;

import com.ecole.saas.finance.domain.ApprovalStatus;
import com.ecole.saas.finance.domain.PaymentReversalRequest;
import java.time.Instant;
import java.util.UUID;

public record PaymentReversalRequestResponse(
        UUID id,
        UUID paymentId,
        UUID invoiceId,
        UUID studentId,
        long amountXof,
        String reason,
        ApprovalStatus status,
        UUID requestedBy,
        Instant requestedAt,
        UUID decidedBy,
        Instant decidedAt,
        String decisionNote) {

    public static PaymentReversalRequestResponse from(PaymentReversalRequest request) {
        return new PaymentReversalRequestResponse(
                request.getId(),
                request.getPaymentId(),
                request.getInvoiceId(),
                request.getStudentId(),
                request.getAmountXof(),
                request.getReason(),
                request.getStatus(),
                request.getRequestedBy(),
                request.getRequestedAt(),
                request.getDecidedBy(),
                request.getDecidedAt(),
                request.getDecisionNote());
    }
}
