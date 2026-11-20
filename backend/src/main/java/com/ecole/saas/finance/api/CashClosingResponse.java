package com.ecole.saas.finance.api;

import com.ecole.saas.finance.domain.CashClosing;
import com.ecole.saas.finance.domain.CashClosingStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CashClosingResponse(
        UUID id,
        LocalDate closingDate,
        long cashInXof,
        long cashOutReversalsXof,
        long expectedCashXof,
        long countedCashXof,
        long discrepancyXof,
        int paymentCount,
        int reversalCount,
        CashClosingStatus status,
        String notes,
        UUID submittedBy,
        Instant submittedAt,
        UUID validatedBy,
        Instant validatedAt,
        String validationNote) {

    public static CashClosingResponse from(CashClosing closing) {
        return new CashClosingResponse(
                closing.getId(),
                closing.getClosingDate(),
                closing.getCashInXof(),
                closing.getCashOutReversalsXof(),
                closing.getExpectedCashXof(),
                closing.getCountedCashXof(),
                closing.getDiscrepancyXof(),
                closing.getPaymentCount(),
                closing.getReversalCount(),
                closing.getStatus(),
                closing.getNotes(),
                closing.getSubmittedBy(),
                closing.getSubmittedAt(),
                closing.getValidatedBy(),
                closing.getValidatedAt(),
                closing.getValidationNote());
    }
}
