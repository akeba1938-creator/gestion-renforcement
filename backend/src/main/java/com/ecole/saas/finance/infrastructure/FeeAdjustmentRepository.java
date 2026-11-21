package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.ApprovalStatus;
import com.ecole.saas.finance.domain.FeeAdjustment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeeAdjustmentRepository
        extends JpaRepository<FeeAdjustment, UUID>, JpaSpecificationExecutor<FeeAdjustment> {

    List<FeeAdjustment> findByInvoiceIdOrderByRequestedAtDesc(UUID invoiceId);

    boolean existsByInvoiceIdAndStatus(UUID invoiceId, ApprovalStatus status);

    @Query("""
            SELECT a.adjustmentType,
                   a.status,
                   COUNT(a.id),
                   COALESCE(SUM(a.resolvedAmountXof), 0L)
            FROM FeeAdjustment a
            WHERE a.requestedAt >= :startInclusive
              AND a.requestedAt < :endExclusive
            GROUP BY a.adjustmentType, a.status
            """)
    List<Object[]> aggregateForRange(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive);

    @Query("""
            SELECT a.adjustmentType,
                   a.status,
                   COUNT(a.id),
                   COALESCE(SUM(a.resolvedAmountXof), 0L)
            FROM FeeAdjustment a
            WHERE a.invoiceId IN (
                    SELECT i.id FROM StudentInvoice i WHERE i.academicYearId = :academicYearId
                  )
              AND a.requestedAt >= :startInclusive
              AND a.requestedAt < :endExclusive
            GROUP BY a.adjustmentType, a.status
            """)
    List<Object[]> aggregateForYearAndRange(
            @Param("academicYearId") UUID academicYearId,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive);
}
