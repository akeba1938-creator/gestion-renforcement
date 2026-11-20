package com.ecole.saas.finance.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Demande puis approbation encadrée d'une remise ou d'une bourse sur une facture élève.
 */
@Entity
@Table(name = "fee_adjustments")
public class FeeAdjustment extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, updatable = false, length = 20)
    private AdjustmentType adjustmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_mode", nullable = false, updatable = false, length = 20)
    private AdjustmentCalculationMode calculationMode;

    @Column(name = "percentage_value", updatable = false)
    private Integer percentageValue;

    @Column(name = "requested_amount_xof", nullable = false, updatable = false)
    private long requestedAmountXof;

    @Column(name = "resolved_amount_xof", nullable = false, updatable = false)
    private long resolvedAmountXof;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private ApprovalStatus status;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    protected FeeAdjustment() {
    }

    private FeeAdjustment(
            UUID invoiceId,
            UUID studentId,
            AdjustmentType adjustmentType,
            AdjustmentCalculationMode calculationMode,
            Integer percentageValue,
            long requestedAmountXof,
            long resolvedAmountXof,
            String reason,
            UUID requestedBy,
            Instant requestedAt) {
        this.invoiceId = Objects.requireNonNull(invoiceId, "La facture est obligatoire.");
        this.studentId = Objects.requireNonNull(studentId, "L'élève est obligatoire.");
        this.adjustmentType = Objects.requireNonNull(adjustmentType, "Le type d'ajustement est obligatoire.");
        this.calculationMode = Objects.requireNonNull(calculationMode, "Le mode de calcul est obligatoire.");
        this.percentageValue = percentageValue;
        this.requestedAmountXof = MoneyMath.requireNonNegative(requestedAmountXof, "Le montant demandé");
        this.resolvedAmountXof = MoneyMath.requirePositive(resolvedAmountXof, "Le montant résolu");
        this.reason = requireReason(reason, "Le motif de la remise ou bourse est obligatoire.");
        this.status = ApprovalStatus.PENDING_APPROVAL;
        this.requestedBy = Objects.requireNonNull(requestedBy, "L'auteur de la demande est obligatoire.");
        this.requestedAt = Objects.requireNonNull(requestedAt, "La date de demande est obligatoire.");
    }

    public static FeeAdjustment requestFixed(
            UUID invoiceId,
            UUID studentId,
            AdjustmentType type,
            long amountXof,
            String reason,
            UUID requestedBy,
            Instant requestedAt) {
        long validated = MoneyMath.requirePositive(amountXof, "Le montant de la remise ou bourse");
        return new FeeAdjustment(
                invoiceId,
                studentId,
                type,
                AdjustmentCalculationMode.FIXED_AMOUNT,
                null,
                validated,
                validated,
                reason,
                requestedBy,
                requestedAt);
    }

    public static FeeAdjustment requestPercentage(
            UUID invoiceId,
            UUID studentId,
            AdjustmentType type,
            int percentage,
            long invoiceGrossAmountXof,
            String reason,
            UUID requestedBy,
            Instant requestedAt) {
        long resolved = MoneyMath.percentageOf(invoiceGrossAmountXof, percentage);
        if (resolved <= 0L) {
            throw new FinanceValidationException(
                    "adjustment-zero-amount",
                    "Montant calculé nul",
                    "Le pourcentage demandé produit une remise nulle en FCFA.");
        }
        return new FeeAdjustment(
                invoiceId,
                studentId,
                type,
                AdjustmentCalculationMode.PERCENTAGE,
                percentage,
                0L,
                resolved,
                reason,
                requestedBy,
                requestedAt);
    }

    public void approve(UUID actorId, Instant decidedAt, String decisionNote) {
        requirePending();
        UUID validatedActor = Objects.requireNonNull(actorId, "Le valideur est obligatoire.");
        Instant validatedDecidedAt = Objects.requireNonNull(decidedAt, "La date de décision est obligatoire.");
        String validatedNote = normalizeOptional(decisionNote);
        this.status = ApprovalStatus.APPROVED;
        this.decidedBy = validatedActor;
        this.decidedAt = validatedDecidedAt;
        this.decisionNote = validatedNote;
    }

    public void reject(UUID actorId, Instant decidedAt, String decisionNote) {
        requirePending();
        UUID validatedActor = Objects.requireNonNull(actorId, "Le valideur est obligatoire.");
        Instant validatedDecidedAt = Objects.requireNonNull(decidedAt, "La date de décision est obligatoire.");
        String validatedNote = requireReason(decisionNote, "Le motif de rejet est obligatoire.");
        this.status = ApprovalStatus.REJECTED;
        this.decidedBy = validatedActor;
        this.decidedAt = validatedDecidedAt;
        this.decisionNote = validatedNote;
    }

    public LedgerEntryType ledgerEntryType() {
        return this.adjustmentType == AdjustmentType.SCHOLARSHIP
                ? LedgerEntryType.SCHOLARSHIP_CREDIT
                : LedgerEntryType.DISCOUNT_CREDIT;
    }

    private void requirePending() {
        if (this.status != ApprovalStatus.PENDING_APPROVAL) {
            throw new FinanceConflictException(
                    "adjustment-already-decided",
                    "Demande déjà traitée",
                    "Cette demande de remise ou bourse a déjà fait l'objet d'une décision.");
        }
    }

    private static String requireReason(String raw, String message) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("Le motif dépasse la longueur maximale autorisée (500).");
        }
        return trimmed;
    }

    private static String normalizeOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("La note de décision dépasse 500 caractères.");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public AdjustmentType getAdjustmentType() {
        return adjustmentType;
    }

    public AdjustmentCalculationMode getCalculationMode() {
        return calculationMode;
    }

    public Integer getPercentageValue() {
        return percentageValue;
    }

    public long getRequestedAmountXof() {
        return requestedAmountXof;
    }

    public long getResolvedAmountXof() {
        return resolvedAmountXof;
    }

    public String getReason() {
        return reason;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }
}
