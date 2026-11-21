package com.ecole.saas.finance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentAdjustmentLedgerAndClosingDomainTest {

    @Test
    void shouldValidateFeeAdjustmentFixedAndPercentageWorkflow() {
        UUID invoiceId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-15T12:00:00Z");

        FeeAdjustment pct = FeeAdjustment.requestPercentage(
                invoiceId, studentId, AdjustmentType.SCHOLARSHIP, 25, 120_000L, "Bourse d'excellence", requester, now);
        assertThat(pct.getResolvedAmountXof()).isEqualTo(30_000L);
        assertThat(pct.ledgerEntryType()).isEqualTo(LedgerEntryType.SCHOLARSHIP_CREDIT);
        assertThat(pct.getStatus()).isEqualTo(ApprovalStatus.PENDING_APPROVAL);

        pct.approve(approver, now.plusSeconds(60), "Validé en conseil");
        assertThat(pct.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThatThrownBy(() -> pct.reject(approver, now.plusSeconds(120), "Trop tard"))
                .isInstanceOf(FinanceConflictException.class);

        FeeAdjustment fixed = FeeAdjustment.requestFixed(
                invoiceId, studentId, AdjustmentType.DISCOUNT, 10_000L, "Remise fratrie", requester, now);
        assertThat(fixed.ledgerEntryType()).isEqualTo(LedgerEntryType.DISCOUNT_CREDIT);
        assertThatThrownBy(() -> fixed.reject(approver, now.plusSeconds(60), " "))
                .isInstanceOf(IllegalArgumentException.class);
        fixed.reject(approver, now.plusSeconds(60), "Plafond dépassé");
        assertThat(fixed.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
    }

    @Test
    void shouldEnforcePaymentReferenceAndIdempotencyMatchingRules() {
        UUID invoiceId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-15T14:00:00Z");

        // Mobile money exige une référence externe
        assertThatThrownBy(() -> new Payment(
                invoiceId, studentId, null, null, 25_000L,
                PaymentMethod.MOBILE_MONEY_WAVE, null, PaymentConfirmationSource.MANUAL_DESK,
                "idem-key-12345678", now, actorId, null))
                .isInstanceOf(FinanceValidationException.class);

        // Clé d'idempotence trop courte
        assertThatThrownBy(() -> new Payment(
                invoiceId, studentId, null, null, 25_000L,
                PaymentMethod.CASH, null, PaymentConfirmationSource.MANUAL_DESK,
                "short", now, actorId, null))
                .isInstanceOf(FinanceValidationException.class);

        UUID guardianId = UUID.randomUUID();
        Payment wavePayment = new Payment(
                invoiceId, studentId, guardianId, null, 25_000L,
                PaymentMethod.MOBILE_MONEY_WAVE, "WAVE-TX-998877", PaymentConfirmationSource.MANUAL_DESK,
                "idem-wave-0001", now, actorId, "Paiement Octobre");

        assertThat(wavePayment.matchesIdempotentRequest(
                invoiceId, 25_000L, PaymentMethod.MOBILE_MONEY_WAVE, "WAVE-TX-998877", null, guardianId, "Paiement Octobre")).isTrue();
        assertThat(wavePayment.matchesIdempotentRequest(
                invoiceId, 26_000L, PaymentMethod.MOBILE_MONEY_WAVE, "WAVE-TX-998877", null, guardianId, "Paiement Octobre")).isFalse();
        assertThat(wavePayment.matchesIdempotentRequest(
                invoiceId, 25_000L, PaymentMethod.MOBILE_MONEY_WAVE, "WAVE-TX-998877", null, UUID.randomUUID(), "Paiement Octobre")).isFalse();
        assertThat(wavePayment.matchesIdempotentRequest(
                invoiceId, 25_000L, PaymentMethod.MOBILE_MONEY_WAVE, "WAVE-TX-998877", null, guardianId, "Autre note")).isFalse();
    }

    @Test
    void shouldForbidSelfApprovalOnAdjustmentReversalAndCashClosing() {
        UUID invoiceId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID sameActor = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-15T15:00:00Z");

        FeeAdjustment adjustment = FeeAdjustment.requestFixed(
                invoiceId, studentId, AdjustmentType.DISCOUNT, 10_000L, "Remise fratrie", sameActor, now);
        assertThatThrownBy(() -> adjustment.approve(sameActor, now.plusSeconds(30), "Auto-approbation"))
                .isInstanceOf(FinanceForbiddenException.class)
                .hasMessageContaining("lui-même");

        PaymentReversalRequest reversalRequest = new PaymentReversalRequest(
                paymentId, invoiceId, studentId, 25_000L, "Erreur saisie", sameActor, now);
        assertThatThrownBy(() -> reversalRequest.approve(sameActor, now.plusSeconds(30), "Auto-approbation"))
                .isInstanceOf(FinanceForbiddenException.class)
                .hasMessageContaining("lui-même");

        CashClosing closing = new CashClosing(
                LocalDate.of(2026, 10, 15), 50_000L, 0L, 50_000L, 2, 0, null, sameActor, now);
        assertThatThrownBy(() -> closing.validate(sameActor, now.plusSeconds(30), "Auto-validation"))
                .isInstanceOf(FinanceForbiddenException.class)
                .hasMessageContaining("lui-même");
    }

    @Test
    void shouldComputeSignedLedgerDeltasAndValidateCashClosingDiscrepancy() {
        UUID studentId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-15T16:00:00Z");

        LedgerEntry charge = new LedgerEntry(
                1L, studentId, invoiceId, LedgerEntryType.CHARGE, 100_000L,
                LedgerReferenceType.INVOICE, invoiceId, null, "Facture", actorId, now);
        LedgerEntry discount = new LedgerEntry(
                2L, studentId, invoiceId, LedgerEntryType.DISCOUNT_CREDIT, 15_000L,
                LedgerReferenceType.ADJUSTMENT, UUID.randomUUID(), null, "Remise", actorId, now);
        LedgerEntry payment = new LedgerEntry(
                3L, studentId, invoiceId, LedgerEntryType.PAYMENT_CREDIT, 50_000L,
                LedgerReferenceType.PAYMENT, UUID.randomUUID(), null, "Reçu", actorId, now);
        LedgerEntry reversal = new LedgerEntry(
                4L, studentId, invoiceId, LedgerEntryType.PAYMENT_REVERSAL, 50_000L,
                LedgerReferenceType.PAYMENT_REVERSAL, UUID.randomUUID(), charge.getId(), "Erreur", actorId, now);

        long netBalance = charge.signedBalanceDeltaXof()
                + discount.signedBalanceDeltaXof()
                + payment.signedBalanceDeltaXof()
                + reversal.signedBalanceDeltaXof();
        assertThat(netBalance).isEqualTo(85_000L);

        // Clôture de caisse avec écart sans justification -> refusée
        assertThatThrownBy(() -> new CashClosing(
                LocalDate.of(2026, 10, 15), 80_000L, 10_000L, 69_000L, 4, 1, null, actorId, now))
                .isInstanceOf(FinanceValidationException.class);

        CashClosing closing = new CashClosing(
                LocalDate.of(2026, 10, 15), 80_000L, 10_000L, 69_000L, 4, 1, "Billet déchiré", actorId, now);
        assertThat(closing.getExpectedCashXof()).isEqualTo(70_000L);
        assertThat(closing.getDiscrepancyXof()).isEqualTo(-1_000L);
        assertThat(closing.getStatus()).isEqualTo(CashClosingStatus.SUBMITTED);

        closing.validate(UUID.randomUUID(), now.plusSeconds(300), "Écart vérifié");
        assertThat(closing.getStatus()).isEqualTo(CashClosingStatus.VALIDATED);
        assertThatThrownBy(() -> closing.validate(UUID.randomUUID(), now.plusSeconds(600), "Re-validation"))
                .isInstanceOf(FinanceConflictException.class);
    }
}
