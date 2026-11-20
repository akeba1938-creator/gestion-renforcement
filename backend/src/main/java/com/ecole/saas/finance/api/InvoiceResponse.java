package com.ecole.saas.finance.api;

import com.ecole.saas.finance.domain.InvoiceStatus;
import com.ecole.saas.finance.domain.StudentInvoice;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        String invoiceNumber,
        UUID studentId,
        UUID academicYearId,
        UUID enrollmentId,
        UUID payerGuardianId,
        UUID familyId,
        String title,
        String currency,
        long grossAmountXof,
        long discountAmountXof,
        long netAmountXof,
        long paidAmountXof,
        long balanceXof,
        InvoiceStatus status,
        LocalDate issueDate,
        LocalDate dueDate,
        Instant issuedAt,
        UUID issuedBy,
        Instant cancelledAt,
        UUID cancelledBy,
        String cancellationReason,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static InvoiceResponse from(StudentInvoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getStudentId(),
                invoice.getAcademicYearId(),
                invoice.getEnrollmentId(),
                invoice.getPayerGuardianId(),
                invoice.getFamilyId(),
                invoice.getTitle(),
                invoice.getCurrency(),
                invoice.getGrossAmountXof(),
                invoice.getDiscountAmountXof(),
                invoice.getNetAmountXof(),
                invoice.getPaidAmountXof(),
                invoice.getBalanceXof(),
                invoice.getStatus(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getIssuedAt(),
                invoice.getIssuedBy(),
                invoice.getCancelledAt(),
                invoice.getCancelledBy(),
                invoice.getCancellationReason(),
                invoice.getNotes(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt());
    }
}
