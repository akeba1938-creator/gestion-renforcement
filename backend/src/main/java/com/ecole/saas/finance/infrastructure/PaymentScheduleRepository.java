package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.PaymentSchedule;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentScheduleRepository extends JpaRepository<PaymentSchedule, UUID> {

    List<PaymentSchedule> findByInvoiceIdOrderByInstallmentNoAsc(UUID invoiceId);

    List<PaymentSchedule> findByInvoiceIdIn(Collection<UUID> invoiceIds);

    void deleteByInvoiceId(UUID invoiceId);

    @Query("""
            SELECT COUNT(s), COALESCE(SUM(s.amountXof - s.paidAmountXof), 0L)
            FROM PaymentSchedule s
            JOIN StudentInvoice i ON i.id = s.invoiceId
            WHERE i.status IN (
                    com.ecole.saas.finance.domain.InvoiceStatus.ISSUED,
                    com.ecole.saas.finance.domain.InvoiceStatus.PARTIALLY_PAID,
                    com.ecole.saas.finance.domain.InvoiceStatus.PAID)
              AND s.status IN (
                    com.ecole.saas.finance.domain.InstallmentStatus.PENDING,
                    com.ecole.saas.finance.domain.InstallmentStatus.PARTIALLY_PAID)
              AND s.amountXof > s.paidAmountXof
              AND s.dueDate < :referenceDate
            """)
    List<Object[]> aggregateOverdueAll(@Param("referenceDate") LocalDate referenceDate);

    @Query("""
            SELECT COUNT(s), COALESCE(SUM(s.amountXof - s.paidAmountXof), 0L)
            FROM PaymentSchedule s
            JOIN StudentInvoice i ON i.id = s.invoiceId
            WHERE i.academicYearId = :academicYearId
              AND i.status IN (
                    com.ecole.saas.finance.domain.InvoiceStatus.ISSUED,
                    com.ecole.saas.finance.domain.InvoiceStatus.PARTIALLY_PAID,
                    com.ecole.saas.finance.domain.InvoiceStatus.PAID)
              AND s.status IN (
                    com.ecole.saas.finance.domain.InstallmentStatus.PENDING,
                    com.ecole.saas.finance.domain.InstallmentStatus.PARTIALLY_PAID)
              AND s.amountXof > s.paidAmountXof
              AND s.dueDate < :referenceDate
            """)
    List<Object[]> aggregateOverdueForYear(
            @Param("academicYearId") UUID academicYearId,
            @Param("referenceDate") LocalDate referenceDate);

    @Query("""
            SELECT i.studentId, COUNT(s)
            FROM PaymentSchedule s
            JOIN StudentInvoice i ON i.id = s.invoiceId
            WHERE i.studentId IN :studentIds
              AND i.status IN (
                    com.ecole.saas.finance.domain.InvoiceStatus.ISSUED,
                    com.ecole.saas.finance.domain.InvoiceStatus.PARTIALLY_PAID,
                    com.ecole.saas.finance.domain.InvoiceStatus.PAID)
              AND s.status IN (
                    com.ecole.saas.finance.domain.InstallmentStatus.PENDING,
                    com.ecole.saas.finance.domain.InstallmentStatus.PARTIALLY_PAID)
              AND s.amountXof > s.paidAmountXof
              AND s.dueDate < :referenceDate
            GROUP BY i.studentId
            """)
    List<Object[]> countOverdueByStudentIds(
            @Param("studentIds") Collection<UUID> studentIds,
            @Param("referenceDate") LocalDate referenceDate);

    @Query("""
            SELECT i.studentId, COUNT(s)
            FROM PaymentSchedule s
            JOIN StudentInvoice i ON i.id = s.invoiceId
            WHERE i.studentId IN :studentIds
              AND i.academicYearId = :academicYearId
              AND i.status IN (
                    com.ecole.saas.finance.domain.InvoiceStatus.ISSUED,
                    com.ecole.saas.finance.domain.InvoiceStatus.PARTIALLY_PAID,
                    com.ecole.saas.finance.domain.InvoiceStatus.PAID)
              AND s.status IN (
                    com.ecole.saas.finance.domain.InstallmentStatus.PENDING,
                    com.ecole.saas.finance.domain.InstallmentStatus.PARTIALLY_PAID)
              AND s.amountXof > s.paidAmountXof
              AND s.dueDate < :referenceDate
            GROUP BY i.studentId
            """)
    List<Object[]> countOverdueByStudentIdsAndYear(
            @Param("studentIds") Collection<UUID> studentIds,
            @Param("academicYearId") UUID academicYearId,
            @Param("referenceDate") LocalDate referenceDate);
}
