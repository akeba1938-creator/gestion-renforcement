package com.ecole.saas.finance;

import java.util.List;
import java.util.UUID;

public record FamilyFinancialSummary(
        UUID familyId,
        String familyName,
        UUID academicYearId,
        long totalGrossXof,
        long totalDiscountXof,
        long totalNetXof,
        long totalPaidXof,
        long totalBalanceXof,
        long ledgerBalanceXof,
        int studentCount,
        List<StudentFinancialSummary> students) {
}
