package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.StudentInvoice;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentInvoiceRepository
        extends JpaRepository<StudentInvoice, UUID>, JpaSpecificationExecutor<StudentInvoice> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM StudentInvoice i WHERE i.id = :id")
    Optional<StudentInvoice> findByIdForUpdate(@Param("id") UUID id);

    Optional<StudentInvoice> findByInvoiceNumber(String invoiceNumber);

    List<StudentInvoice> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<StudentInvoice> findByStudentIdAndAcademicYearIdOrderByCreatedAtDesc(UUID studentId, UUID academicYearId);

    List<StudentInvoice> findByStudentIdIn(Collection<UUID> studentIds);

    List<StudentInvoice> findByStudentIdInAndAcademicYearId(Collection<UUID> studentIds, UUID academicYearId);

    List<StudentInvoice> findByAcademicYearId(UUID academicYearId);

    @Query("""
            SELECT i.status,
                   COUNT(i),
                   COALESCE(SUM(i.grossAmountXof), 0L),
                   COALESCE(SUM(i.discountAmountXof), 0L),
                   COALESCE(SUM(i.netAmountXof), 0L),
                   COALESCE(SUM(i.paidAmountXof), 0L),
                   COALESCE(SUM(i.balanceXof), 0L)
            FROM StudentInvoice i
            GROUP BY i.status
            """)
    List<Object[]> aggregateByStatusAll();

    @Query("""
            SELECT i.status,
                   COUNT(i),
                   COALESCE(SUM(i.grossAmountXof), 0L),
                   COALESCE(SUM(i.discountAmountXof), 0L),
                   COALESCE(SUM(i.netAmountXof), 0L),
                   COALESCE(SUM(i.paidAmountXof), 0L),
                   COALESCE(SUM(i.balanceXof), 0L)
            FROM StudentInvoice i
            WHERE i.academicYearId = :academicYearId
            GROUP BY i.status
            """)
    List<Object[]> aggregateByStatusForYear(@Param("academicYearId") UUID academicYearId);

    @Query("""
            SELECT i.studentId,
                   COUNT(i),
                   COALESCE(SUM(i.grossAmountXof), 0L),
                   COALESCE(SUM(i.discountAmountXof), 0L),
                   COALESCE(SUM(i.netAmountXof), 0L),
                   COALESCE(SUM(i.paidAmountXof), 0L),
                   COALESCE(SUM(i.balanceXof), 0L)
            FROM StudentInvoice i
            WHERE i.studentId IN :studentIds
              AND i.status IN (
                    com.ecole.saas.finance.domain.InvoiceStatus.ISSUED,
                    com.ecole.saas.finance.domain.InvoiceStatus.PARTIALLY_PAID,
                    com.ecole.saas.finance.domain.InvoiceStatus.PAID)
            GROUP BY i.studentId
            """)
    List<Object[]> aggregateActiveByStudentIds(@Param("studentIds") Collection<UUID> studentIds);

    @Query("""
            SELECT i.studentId,
                   COUNT(i),
                   COALESCE(SUM(i.grossAmountXof), 0L),
                   COALESCE(SUM(i.discountAmountXof), 0L),
                   COALESCE(SUM(i.netAmountXof), 0L),
                   COALESCE(SUM(i.paidAmountXof), 0L),
                   COALESCE(SUM(i.balanceXof), 0L)
            FROM StudentInvoice i
            WHERE i.studentId IN :studentIds
              AND i.academicYearId = :academicYearId
              AND i.status IN (
                    com.ecole.saas.finance.domain.InvoiceStatus.ISSUED,
                    com.ecole.saas.finance.domain.InvoiceStatus.PARTIALLY_PAID,
                    com.ecole.saas.finance.domain.InvoiceStatus.PAID)
            GROUP BY i.studentId
            """)
    List<Object[]> aggregateActiveByStudentIdsAndYear(
            @Param("studentIds") Collection<UUID> studentIds,
            @Param("academicYearId") UUID academicYearId);
}
