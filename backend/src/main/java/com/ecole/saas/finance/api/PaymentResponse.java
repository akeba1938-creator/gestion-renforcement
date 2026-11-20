package com.ecole.saas.finance.api;

import com.ecole.saas.finance.application.PaymentService;
import com.ecole.saas.finance.domain.Payment;
import com.ecole.saas.finance.domain.PaymentConfirmationSource;
import com.ecole.saas.finance.domain.PaymentMethod;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID invoiceId,
        UUID studentId,
        UUID payerGuardianId,
        UUID requestedScheduleId,
        long amountXof,
        String currency,
        PaymentMethod paymentMethod,
        String externalReference,
        PaymentConfirmationSource confirmationSource,
        String idempotencyKey,
        Instant paidAt,
        UUID recordedBy,
        String notes,
        boolean reversed,
        Instant reversedAt,
        ReceiptResponse receipt,
        List<PaymentAllocationResponse> allocations) {

    public static PaymentResponse from(PaymentService.PaymentView view) {
        Payment payment = view.payment();
        return new PaymentResponse(
                payment.getId(),
                payment.getInvoiceId(),
                payment.getStudentId(),
                payment.getPayerGuardianId(),
                payment.getRequestedScheduleId(),
                payment.getAmountXof(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                payment.getExternalReference(),
                payment.getConfirmationSource(),
                payment.getIdempotencyKey(),
                payment.getPaidAt(),
                payment.getRecordedBy(),
                payment.getNotes(),
                view.reversed(),
                view.reversal() != null ? view.reversal().getReversedAt() : null,
                ReceiptResponse.from(view.receipt()),
                view.allocations().stream().map(PaymentAllocationResponse::from).toList());
    }
}
