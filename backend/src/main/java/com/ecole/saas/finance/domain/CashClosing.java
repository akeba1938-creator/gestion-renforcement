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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Clôture journalière de caisse avec calcul automatique de l'attendu espèces et constat d'écart.
 */
@Entity
@Table(name = "cash_closings")
public class CashClosing extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "closing_date", nullable = false, updatable = false)
    private LocalDate closingDate;

    @Column(name = "cash_in_xof", nullable = false, updatable = false)
    private long cashInXof;

    @Column(name = "cash_out_reversals_xof", nullable = false, updatable = false)
    private long cashOutReversalsXof;

    @Column(name = "expected_cash_xof", nullable = false, updatable = false)
    private long expectedCashXof;

    @Column(name = "counted_cash_xof", nullable = false, updatable = false)
    private long countedCashXof;

    @Column(name = "discrepancy_xof", nullable = false, updatable = false)
    private long discrepancyXof;

    @Column(name = "payment_count", nullable = false, updatable = false)
    private int paymentCount;

    @Column(name = "reversal_count", nullable = false, updatable = false)
    private int reversalCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CashClosingStatus status;

    @Column(name = "notes", updatable = false, length = 500)
    private String notes;

    @Column(name = "submitted_by", nullable = false, updatable = false)
    private UUID submittedBy;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "validated_by")
    private UUID validatedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "validation_note", length = 500)
    private String validationNote;

    protected CashClosing() {
    }

    public CashClosing(
            LocalDate closingDate,
            long cashInXof,
            long cashOutReversalsXof,
            long countedCashXof,
            int paymentCount,
            int reversalCount,
            String notes,
            UUID submittedBy,
            Instant submittedAt) {
        this.closingDate = Objects.requireNonNull(closingDate, "La date de clôture est obligatoire.");
        this.cashInXof = MoneyMath.requireNonNegative(cashInXof, "Les encaissements espèces");
        this.cashOutReversalsXof = MoneyMath.requireNonNegative(cashOutReversalsXof, "Les contre-passations espèces");
        this.expectedCashXof = MoneyMath.subtractExact(this.cashInXof, this.cashOutReversalsXof);
        this.countedCashXof = MoneyMath.requireNonNegative(countedCashXof, "Le montant compté en caisse");
        this.discrepancyXof = MoneyMath.subtractExact(this.countedCashXof, this.expectedCashXof);
        if (paymentCount < 0 || reversalCount < 0) {
            throw new IllegalArgumentException("Les compteurs d'opérations doivent être positifs ou nuls.");
        }
        this.paymentCount = paymentCount;
        this.reversalCount = reversalCount;
        String normalizedNotes = normalizeOptional(notes);
        if (this.discrepancyXof != 0L && normalizedNotes == null) {
            throw new FinanceValidationException(
                    "cash-closing-discrepancy-note-required",
                    "Justification d'écart requise",
                    "Une justification est obligatoire lorsque l'écart de caisse est différent de zéro.");
        }
        this.notes = normalizedNotes;
        this.status = CashClosingStatus.SUBMITTED;
        this.submittedBy = Objects.requireNonNull(submittedBy, "L'auteur de la clôture est obligatoire.");
        this.submittedAt = Objects.requireNonNull(submittedAt, "L'horodatage de clôture est obligatoire.");
    }

    public void validate(UUID validatorId, Instant validatedAt, String validationNote) {
        if (this.status == CashClosingStatus.VALIDATED) {
            throw new FinanceConflictException(
                    "cash-closing-already-validated",
                    "Clôture déjà validée",
                    "Cette clôture de caisse est déjà validée.");
        }
        UUID validatedActor = Objects.requireNonNull(validatorId, "Le valideur est obligatoire.");
        Instant validatedAtChecked = Objects.requireNonNull(validatedAt, "La date de validation est obligatoire.");
        String normalizedNote = normalizeOptional(validationNote);
        this.status = CashClosingStatus.VALIDATED;
        this.validatedBy = validatedActor;
        this.validatedAt = validatedAtChecked;
        this.validationNote = normalizedNote;
    }

    private static String normalizeOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("La note dépasse la longueur maximale autorisée (500).");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getClosingDate() {
        return closingDate;
    }

    public long getCashInXof() {
        return cashInXof;
    }

    public long getCashOutReversalsXof() {
        return cashOutReversalsXof;
    }

    public long getExpectedCashXof() {
        return expectedCashXof;
    }

    public long getCountedCashXof() {
        return countedCashXof;
    }

    public long getDiscrepancyXof() {
        return discrepancyXof;
    }

    public int getPaymentCount() {
        return paymentCount;
    }

    public int getReversalCount() {
        return reversalCount;
    }

    public CashClosingStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public UUID getValidatedBy() {
        return validatedBy;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public String getValidationNote() {
        return validationNote;
    }
}
