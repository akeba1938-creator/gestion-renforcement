package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.CashClosing;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashClosingRepository
        extends JpaRepository<CashClosing, UUID>, JpaSpecificationExecutor<CashClosing> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CashClosing c WHERE c.id = :id")
    Optional<CashClosing> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByClosingDate(LocalDate closingDate);

    Optional<CashClosing> findByClosingDate(LocalDate closingDate);

    @Query("""
            SELECT
                COUNT(c.id),
                COALESCE(SUM(CASE WHEN c.status = com.ecole.saas.finance.domain.CashClosingStatus.SUBMITTED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN c.status = com.ecole.saas.finance.domain.CashClosingStatus.VALIDATED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(c.expectedCashXof), 0L),
                COALESCE(SUM(c.countedCashXof), 0L),
                COALESCE(SUM(c.discrepancyXof), 0L)
            FROM CashClosing c
            WHERE c.closingDate >= :fromDate
              AND c.closingDate <= :toDate
            """)
    List<Object[]> aggregateForDateRange(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
