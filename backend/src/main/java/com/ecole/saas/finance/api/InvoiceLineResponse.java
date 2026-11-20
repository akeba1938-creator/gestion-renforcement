package com.ecole.saas.finance.api;

import com.ecole.saas.finance.domain.FeeCategory;
import com.ecole.saas.finance.domain.StudentInvoiceLine;
import java.util.UUID;

public record InvoiceLineResponse(
        UUID id,
        UUID feeItemId,
        int lineOrder,
        String feeCode,
        String label,
        FeeCategory category,
        int quantity,
        long unitAmountXof,
        long lineTotalXof) {

    public static InvoiceLineResponse from(StudentInvoiceLine line) {
        return new InvoiceLineResponse(
                line.getId(),
                line.getFeeItemId(),
                line.getLineOrder(),
                line.getFeeCode(),
                line.getLabel(),
                line.getCategory(),
                line.getQuantity(),
                line.getUnitAmountXof(),
                line.getLineTotalXof());
    }
}
