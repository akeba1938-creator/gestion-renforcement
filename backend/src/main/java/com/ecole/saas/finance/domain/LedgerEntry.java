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
 * Écriture du registre comptable — STRICTEMENT IMMUABLE EN AJOUT SEUL (ADR 0003).
 * Aucune modification ni suppression ; toute correction passe par une contre-écriture tracée.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private long sequenceNo;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 30)
    private LedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, updatable = false, length = 10)
    private LedgerDirection direction;

    @Column(name = "amount_xof", nullable = false, updatable = false)
    private long amountXof;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, updatable = false, length = 30)
    private LedgerReferenceType referenceType;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;

    @Column(name = "reversal_of_entry_id", updatable = false)
    private UUID reversalOfEntryId;

    @Column(name = "reason", updatable = false, length = 500)
    private String reason;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(
            long sequenceNo,
            UUID studentId,
            UUID invoiceId,
            LedgerEntryType entryType,
            long amountXof,
            LedgerReferenceType referenceType,
            UUID referenceId,
            UUID reversalOfEntryId,
            String reason,
            UUID actorId,
            Instant occurredAt) {
        if (sequenceNo <= 0L) {
            throw new IllegalArgumentException("Le numéro de séquence comptable doit être strictement positif.");
        }
        this.sequenceNo = sequenceNo;
        this.studentId = Objects.requireNonNull(studentId, "L'élève est obligatoire.");
        this.invoiceId = Objects.requireNonNull(invoiceId, "La facture est obligatoire.");
        this.entryType = Objects.requireNonNull(entryType, "Le type d'écriture comptable est obligatoire.");
        this.direction = entryType.defaultDirection();
        this.amountXof = MoneyMath.requirePositive(amountXof, "Le montant de l'écriture comptable");
        this.currency = MoneyMath.CURRENCY_XOF;
        this.referenceType = Objects.requireNonNull(referenceType, "Le type de référence est obligatoire.");
        this.referenceId = Objects.requireNonNull(referenceId, "L'identifiant de référence est obligatoire.");
        this.reversalOfEntryId = reversalOfEntryId;
        this.reason = (reason == null || reason.isBlank()) ? null : reason.trim();
        this.actorId = Objects.requireNonNull(actorId, "L'acteur comptable est obligatoire.");
        this.occurredAt = Objects.requireNonNull(occurredAt, "L'horodatage comptable est obligatoire.");
    }

    /**
     * Contribution signée au solde dû par l'élève : +amountXof pour DEBIT, -amountXof pour CREDIT.
     */
    public long signedBalanceDeltaXof() {
        return this.direction == LedgerDirection.DEBIT ? this.amountXof : -this.amountXof;
    }

    public UUID getId() {
        return id;
    }

    public long getSequenceNo() {
        return sequenceNo;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public LedgerDirection getDirection() {
        return direction;
    }

    public long getAmountXof() {
        return amountXof;
    }

    public String getCurrency() {
        return currency;
    }

    public LedgerReferenceType getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public UUID getReversalOfEntryId() {
        return reversalOfEntryId;
    }

    public String getReason() {
        return reason;
    }

    public UUID getActorId() {
        return actorId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
