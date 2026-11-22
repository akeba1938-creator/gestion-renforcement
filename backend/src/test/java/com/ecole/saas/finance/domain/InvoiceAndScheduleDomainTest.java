package com.ecole.saas.finance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceAndScheduleDomainTest {

    @Test
    void shouldManageFeeItemLifecycleAndRejectUpdateWhenInactive() {
        UUID yearId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        FeeItem item = new FeeItem(
                yearId,
                levelId,
                "mens-6e",
                "Mensualité 6ème",
                FeeCategory.TUITION,
                FeeFrequency.MONTHLY,
                25_000L,
                true,
                "Frais mensuels");

        assertThat(item.getCode()).isEqualTo("MENS-6E");
        assertThat(item.isActive()).isTrue();

        item.update(null, "Mensualité générale", FeeCategory.TUITION, FeeFrequency.MONTHLY, 30_000L, true, null);
        assertThat(item.getAmountXof()).isEqualTo(30_000L);
        assertThat(item.getGradeLevelId()).isNull();

        item.deactivate();
        assertThat(item.isActive()).isFalse();
        assertThatThrownBy(() -> item.update(null, "Modif", FeeCategory.TUITION, FeeFrequency.MONTHLY, 35_000L, true, null))
                .isInstanceOf(FinanceConflictException.class);

        item.activate();
        assertThat(item.isActive()).isTrue();
    }

    @Test
    void shouldEnforceInvoiceStateTransitionsPaymentsAdjustmentsAndReversals() {
        UUID studentId = UUID.randomUUID();
        UUID yearId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-15T10:00:00Z");

        StudentInvoice invoice = new StudentInvoice(
                "FAC-IND-2026-000001",
                studentId,
                yearId,
                null,
                null,
                null,
                "Scolarité 2026-2027",
                LocalDate.of(2026, 11, 5),
                null,
                actorId);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);

        assertThatThrownBy(() -> invoice.issue(LocalDate.of(2026, 10, 15), now, actorId))
                .isInstanceOf(FinanceValidationException.class);

        invoice.recalculateDraftGross(100_000L);
        invoice.issue(LocalDate.of(2026, 10, 15), now, actorId);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoice.getBalanceXof()).isEqualTo(100_000L);

        assertThatThrownBy(() -> invoice.recalculateDraftGross(120_000L))
                .isInstanceOf(FinanceConflictException.class);

        invoice.applyApprovedAdjustment(20_000L);
        assertThat(invoice.getDiscountAmountXof()).isEqualTo(20_000L);
        assertThat(invoice.getNetAmountXof()).isEqualTo(80_000L);
        assertThat(invoice.getBalanceXof()).isEqualTo(80_000L);

        invoice.applyPayment(50_000L);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(invoice.getPaidAmountXof()).isEqualTo(50_000L);
        assertThat(invoice.getBalanceXof()).isEqualTo(30_000L);

        assertThatThrownBy(() -> invoice.applyPayment(30_001L))
                .isInstanceOf(FinanceConflictException.class);
        assertThatThrownBy(() -> invoice.cancel("Erreur", now, actorId))
                .isInstanceOf(FinanceConflictException.class);

        invoice.applyPayment(30_000L);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getBalanceXof()).isZero();

        invoice.reversePayment(30_000L);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(invoice.getPaidAmountXof()).isEqualTo(50_000L);
        assertThat(invoice.getBalanceXof()).isEqualTo(30_000L);

        invoice.reversePayment(50_000L);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoice.getPaidAmountXof()).isZero();

        boolean wasIssued = invoice.cancel("Élève désinscrit avant rentrée", now, actorId);
        assertThat(wasIssued).isTrue();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThat(invoice.getBalanceXof()).isZero();
    }

    @Test
    void shouldAllocateFifoTargetedAndReduceSchedulesFromLastInstallment() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        PaymentSchedule s1 = scheduleWithId(invoiceId, 1, LocalDate.of(2026, 10, 5), 40_000L);
        PaymentSchedule s2 = scheduleWithId(invoiceId, 2, LocalDate.of(2026, 11, 5), 30_000L);
        PaymentSchedule s3 = scheduleWithId(invoiceId, 3, LocalDate.of(2026, 12, 5), 30_000L);
        List<PaymentSchedule> schedules = List.of(s1, s2, s3);

        // Réduction de 20 000 FCFA sur la dernière échéance
        ScheduleAllocator.reduceFromLastInstallment(schedules, 20_000L);
        assertThat(s3.getAmountXof()).isEqualTo(10_000L);
        assertThat(s2.getAmountXof()).isEqualTo(30_000L);
        assertThat(s1.getAmountXof()).isEqualTo(40_000L);

        // Paiement FIFO de 50 000 FCFA -> 40 000 sur s1 (PAID), 10 000 sur s2 (PARTIALLY_PAID)
        List<ScheduleAllocator.AllocationPlan> plans = ScheduleAllocator.allocatePayment(schedules, 50_000L, null);
        assertThat(plans).hasSize(2);
        assertThat(s1.getStatus()).isEqualTo(InstallmentStatus.PAID);
        assertThat(s2.getStatus()).isEqualTo(InstallmentStatus.PARTIALLY_PAID);
        assertThat(s2.getPaidAmountXof()).isEqualTo(10_000L);
        assertThat(s2.isOverdueAt(LocalDate.of(2026, 11, 6))).isTrue();
        assertThat(s2.isOverdueAt(LocalDate.of(2026, 11, 5))).isFalse();

        // Paiement ciblé sur s3 (10 000 FCFA)
        List<ScheduleAllocator.AllocationPlan> targeted =
                ScheduleAllocator.allocatePayment(schedules, 10_000L, s3.getId());
        assertThat(targeted).hasSize(1);
        assertThat(s3.getStatus()).isEqualTo(InstallmentStatus.PAID);

        // Contre-passation sur s3
        s3.reversePayment(10_000L);
        assertThat(s3.getStatus()).isEqualTo(InstallmentStatus.PENDING);
        assertThat(s3.remainingAmountXof()).isEqualTo(10_000L);
    }

    private static PaymentSchedule scheduleWithId(UUID invoiceId, int no, LocalDate dueDate, long amountXof)
            throws Exception {
        PaymentSchedule schedule = new PaymentSchedule(invoiceId, no, "Échéance " + no, dueDate, amountXof);
        Field idField = PaymentSchedule.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(schedule, UUID.randomUUID());
        return schedule;
    }
}
