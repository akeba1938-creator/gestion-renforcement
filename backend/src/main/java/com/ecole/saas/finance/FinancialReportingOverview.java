package com.ecole.saas.finance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Synthèse financière consolidée d'un établissement (facturation, remises/bourses, encaissements,
 * impayés, contre-passations, registre comptable et clôtures de caisse) strictement en entiers FCFA ({@code long}).
 */
public record FinancialReportingOverview(
        UUID academicYearId,
        LocalDate fromDate,
        LocalDate toDate,
        long totalInvoiceCount,
        long draftInvoiceCount,
        long issuedInvoiceCount,
        long partiallyPaidInvoiceCount,
        long paidInvoiceCount,
        long cancelledInvoiceCount,
        long totalGrossXof,
        long totalDiscountXof,
        long totalNetXof,
        long totalInvoicePaidXof,
        long totalOutstandingXof,
        long overdueInstallmentCount,
        long overdueAmountXof,
        long approvedDiscountCount,
        long approvedDiscountXof,
        long approvedScholarshipCount,
        long approvedScholarshipXof,
        long pendingAdjustmentCount,
        long pendingAdjustmentXof,
        long collectedPaymentsCount,
        long collectedPaymentsXof,
        long reversalsCount,
        long reversalsXof,
        long netCollectedXof,
        long ledgerNetBalanceXof,
        boolean ledgerConsistentWithInvoices,
        long cashClosingTotalCount,
        long cashClosingSubmittedCount,
        long cashClosingValidatedCount,
        long cashClosingExpectedXof,
        long cashClosingCountedXof,
        long cashClosingDiscrepancyXof,
        List<PaymentMethodBreakdown> paymentMethodBreakdowns) {
}
