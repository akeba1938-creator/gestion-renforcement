package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.ApprovalStatus;
import com.ecole.saas.finance.domain.FeeAdjustment;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeeAdjustmentRepository
        extends JpaRepository<FeeAdjustment, UUID>, JpaSpecificationExecutor<FeeAdjustment> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM FeeAdjustment a WHERE a.id = :id")
    Optional<FeeAdjustment> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT COALESCE(SUM(a.resolvedAmountXof), 0L)
            FROM FeeAdjustment a
            WHERE a.invoiceId = :invoiceId
              AND a.status = :status
            """)
    long sumResolvedAmountByInvoiceIdAndStatus(
            @Param("invoiceId") UUID invoiceId,
            @Param("status") ApprovalStatus status);

    List<FeeAdjustment> findByInvoiceIdOrderByRequestedAtDesc(UUID invoiceId);

    List<FeeAdjustment> findByInvoiceIdAndStatus(UUID invoiceId, ApprovalStatus status);

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
