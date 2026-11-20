package com.ecole.saas.finance.api;

import com.ecole.saas.finance.domain.AdjustmentCalculationMode;
import com.ecole.saas.finance.domain.AdjustmentType;
import com.ecole.saas.finance.domain.ApprovalStatus;
import com.ecole.saas.finance.domain.FeeAdjustment;
import java.time.Instant;
import java.util.UUID;

public record FeeAdjustmentResponse(
        UUID id,
        UUID invoiceId,
        UUID studentId,
        AdjustmentType adjustmentType,
        AdjustmentCalculationMode calculationMode,
        Integer percentageValue,
        long requestedAmountXof,
        long resolvedAmountXof,
        String reason,
        ApprovalStatus status,
        UUID requestedBy,
        Instant requestedAt,
        UUID decidedBy,
        Instant decidedAt,
        String decisionNote) {

    public static FeeAdjustmentResponse from(FeeAdjustment adjustment) {
        return new FeeAdjustmentResponse(
                adjustment.getId(),
                adjustment.getInvoiceId(),
                adjustment.getStudentId(),
                adjustment.getAdjustmentType(),
                adjustment.getCalculationMode(),
                adjustment.getPercentageValue(),
                adjustment.getRequestedAmountXof(),
                adjustment.getResolvedAmountXof(),
                adjustment.getReason(),
                adjustment.getStatus(),
                adjustment.getRequestedBy(),
                adjustment.getRequestedAt(),
                adjustment.getDecidedBy(),
                adjustment.getDecidedAt(),
                adjustment.getDecisionNote());
    }
}
