package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.LedgerEntry;
import com.ecole.saas.finance.domain.LedgerEntryType;
import com.ecole.saas.finance.domain.LedgerReferenceType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Dépôt en ajout seul sur {@code ledger_entries} (ADR 0003) : aucune méthode de suppression n'est exposée.
 */
public interface LedgerEntryRepository extends Repository<LedgerEntry, UUID>, JpaSpecificationExecutor<LedgerEntry> {

    LedgerEntry save(LedgerEntry entry);

    Optional<LedgerEntry> findById(UUID id);

    List<LedgerEntry> findByStudentIdOrderBySequenceNoAsc(UUID studentId);

    List<LedgerEntry> findByStudentIdIn(Collection<UUID> studentIds);

    List<LedgerEntry> findByInvoiceIdOrderBySequenceNoAsc(UUID invoiceId);

    Optional<LedgerEntry> findFirstByReferenceTypeAndReferenceIdAndEntryType(
            LedgerReferenceType referenceType, UUID referenceId, LedgerEntryType entryType);

    @Query("""
            SELECT COALESCE(SUM(
                CASE WHEN l.direction = com.ecole.saas.finance.domain.LedgerDirection.DEBIT
                     THEN l.amountXof
                     ELSE -l.amountXof
                END
            ), 0L)
            FROM LedgerEntry l
            """)
    long sumSignedNetBalanceAll();

    @Query("""
            SELECT COALESCE(SUM(
                CASE WHEN l.direction = com.ecole.saas.finance.domain.LedgerDirection.DEBIT
                     THEN l.amountXof
                     ELSE -l.amountXof
                END
            ), 0L)
            FROM LedgerEntry l
            WHERE l.invoiceId IN (
                SELECT i.id FROM StudentInvoice i WHERE i.academicYearId = :academicYearId
            )
            """)
    long sumSignedNetBalanceByAcademicYear(@Param("academicYearId") UUID academicYearId);

    @Query("""
            SELECT l.studentId,
                   COALESCE(SUM(
                       CASE WHEN l.direction = com.ecole.saas.finance.domain.LedgerDirection.DEBIT
                            THEN l.amountXof
                            ELSE -l.amountXof
                       END
                   ), 0L)
            FROM LedgerEntry l
            WHERE l.studentId IN :studentIds
            GROUP BY l.studentId
            """)
    List<Object[]> sumSignedNetBalanceByStudentIds(@Param("studentIds") Collection<UUID> studentIds);

    @Query("""
            SELECT l.studentId,
                   COALESCE(SUM(
                       CASE WHEN l.direction = com.ecole.saas.finance.domain.LedgerDirection.DEBIT
                            THEN l.amountXof
                            ELSE -l.amountXof
                       END
                   ), 0L)
            FROM LedgerEntry l
            WHERE l.studentId IN :studentIds
              AND l.invoiceId IN (
                  SELECT i.id FROM StudentInvoice i
                  WHERE i.studentId IN :studentIds
                    AND i.academicYearId = :academicYearId
              )
            GROUP BY l.studentId
            """)
    List<Object[]> sumSignedNetBalanceByStudentIdsAndAcademicYear(
            @Param("studentIds") Collection<UUID> studentIds,
            @Param("academicYearId") UUID academicYearId);
}
