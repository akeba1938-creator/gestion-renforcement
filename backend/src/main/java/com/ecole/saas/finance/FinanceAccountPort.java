package com.ecole.saas.finance;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Frontière publique en lecture seule du module {@code finance} exposée aux autres modules applicatifs
 * ({@code reporting}, {@code communication}, {@code schooldocs}) sans exposer les entités internes.
 */
public interface FinanceAccountPort {

    StudentFinancialSummary getStudentFinancialSummary(UUID studentId, UUID academicYearId);

    List<StudentFinancialSummary> getStudentFinancialSummariesByIds(
            Collection<UUID> studentIds, UUID academicYearId);

    FamilyFinancialSummary getFamilyFinancialSummary(UUID familyId, UUID academicYearId);

    Optional<Long> findStudentLedgerBalanceXof(UUID studentId);

    FinancialReportingOverview getFinancialReportingOverview(
            UUID academicYearId, LocalDate fromDate, LocalDate toDate);
}
