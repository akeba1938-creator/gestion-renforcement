package com.ecole.saas.finance;

import java.util.UUID;

public record StudentFinancialSummary(
        UUID studentId,
        String studentMatricule,
        String studentFirstName,
        String studentLastName,
        UUID academicYearId,
        long totalGrossXof,
        long totalDiscountXof,
        long totalNetXof,
        long totalPaidXof,
        long totalBalanceXof,
        long ledgerBalanceXof,
        int issuedInvoiceCount,
        int overdueInstallmentCount) {
}
