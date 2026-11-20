package com.ecole.saas.finance;

import java.util.Optional;
import java.util.UUID;

/**
 * Frontière publique en lecture seule du module {@code finance} exposée aux autres modules applicatifs
 * ({@code reporting}, {@code communication}, {@code schooldocs}) sans exposer les entités internes.
 */
public interface FinanceAccountPort {

    StudentFinancialSummary getStudentFinancialSummary(UUID studentId, UUID academicYearId);

    FamilyFinancialSummary getFamilyFinancialSummary(UUID familyId, UUID academicYearId);

    Optional<Long> findStudentLedgerBalanceXof(UUID studentId);
}
