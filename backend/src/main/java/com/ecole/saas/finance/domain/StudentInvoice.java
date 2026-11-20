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
 * Facture / dossier de frais d'un élève pour une année scolaire (montants strictement en entiers FCFA).
 */
@Entity
@Table(name = "student_invoices")
public class StudentInvoice extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_number", nullable = false, updatable = false, length = 64)
    private String invoiceNumber;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Column(name = "enrollment_id")
    private UUID enrollmentId;

    @Column(name = "payer_guardian_id")
    private UUID payerGuardianId;

    @Column(name = "family_id")
    private UUID familyId;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "gross_amount_xof", nullable = false)
    private long grossAmountXof;

    @Column(name = "discount_amount_xof", nullable = false)
    private long discountAmountXof;

    @Column(name = "net_amount_xof", nullable = false)
    private long netAmountXof;

    @Column(name = "paid_amount_xof", nullable = false)
    private long paidAmountXof;

    @Column(name = "balance_xof", nullable = false)
    private long balanceXof;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "issued_by")
    private UUID issuedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected StudentInvoice() {
    }

    public StudentInvoice(
            String invoiceNumber,
            UUID studentId,
            UUID academicYearId,
            UUID enrollmentId,
            UUID payerGuardianId,
            UUID familyId,
            String title,
            LocalDate dueDate,
            String notes,
            UUID createdBy) {
        this.invoiceNumber = requireNonBlank(invoiceNumber, "Le numéro de facture est obligatoire.", 64);
        this.studentId = Objects.requireNonNull(studentId, "L'identifiant de l'élève est obligatoire.");
        this.academicYearId = Objects.requireNonNull(academicYearId, "L'année scolaire est obligatoire.");
        this.enrollmentId = enrollmentId;
        this.payerGuardianId = payerGuardianId;
        this.familyId = familyId;
        this.title = requireNonBlank(title, "L'intitulé de la facture est obligatoire.", 150);
        this.currency = MoneyMath.CURRENCY_XOF;
        this.grossAmountXof = 0L;
        this.discountAmountXof = 0L;
        this.netAmountXof = 0L;
        this.paidAmountXof = 0L;
        this.balanceXof = 0L;
        this.status = InvoiceStatus.DRAFT;
        this.dueDate = Objects.requireNonNull(dueDate, "La date d'échéance est obligatoire.");
        this.notes = normalizeOptionalText(notes);
        this.createdBy = createdBy;
    }

    public void updateDraft(
            UUID payerGuardianId,
            UUID familyId,
            String title,
            LocalDate dueDate,
            String notes) {
        requireDraft("Seule une facture en brouillon peut être modifiée.");
        this.payerGuardianId = payerGuardianId;
        this.familyId = familyId;
        this.title = requireNonBlank(title, "L'intitulé de la facture est obligatoire.", 150);
        this.dueDate = Objects.requireNonNull(dueDate, "La date d'échéance est obligatoire.");
        this.notes = normalizeOptionalText(notes);
    }

    public void recalculateDraftGross(long totalGrossXof) {
        requireDraft("Les lignes ne peuvent être recalculées que sur une facture en brouillon.");
        MoneyMath.requireNonNegative(totalGrossXof, "Le montant brut de la facture");
        this.grossAmountXof = totalGrossXof;
        this.discountAmountXof = 0L;
        this.netAmountXof = totalGrossXof;
        this.paidAmountXof = 0L;
        this.balanceXof = totalGrossXof;
    }

    public void issue(LocalDate issueDate, Instant issuedAt, UUID actorId) {
        requireDraft("Seule une facture en brouillon peut être émise.");
        if (this.grossAmountXof <= 0L) {
            throw new FinanceValidationException(
                    "invoice-empty-amount",
                    "Montant nul",
                    "Une facture doit comporter un montant brut strictement positif avant émission.");
        }
        this.issueDate = Objects.requireNonNull(issueDate, "La date d'émission est obligatoire.");
        this.issuedAt = Objects.requireNonNull(issuedAt, "L'horodatage d'émission est obligatoire.");
        this.issuedBy = Objects.requireNonNull(actorId, "L'auteur de l'émission est obligatoire.");
        this.status = InvoiceStatus.ISSUED;
    }

    public void applyApprovedAdjustment(long adjustmentAmountXof) {
        if (this.status != InvoiceStatus.ISSUED && this.status != InvoiceStatus.PARTIALLY_PAID) {
            throw new FinanceConflictException(
                    "invoice-not-adjustable",
                    "Facture non ajustable",
                    "Une remise ou bourse ne peut s'appliquer qu'à une facture émise ou partiellement payée.");
        }
        MoneyMath.requirePositive(adjustmentAmountXof, "Le montant de la remise ou bourse");
        if (adjustmentAmountXof > this.balanceXof) {
            throw new FinanceConflictException(
                    "adjustment-exceeds-balance",
                    "Remise supérieure au solde",
                    "Le montant de la remise ou bourse dépasse le solde restant dû de la facture.");
        }
        this.discountAmountXof = MoneyMath.addExact(this.discountAmountXof, adjustmentAmountXof);
        this.netAmountXof = MoneyMath.subtractExact(this.grossAmountXof, this.discountAmountXof);
        this.balanceXof = MoneyMath.subtractExact(this.netAmountXof, this.paidAmountXof);
        refreshOpenStatus();
    }

    public void applyPayment(long paymentAmountXof) {
        if (this.status != InvoiceStatus.ISSUED && this.status != InvoiceStatus.PARTIALLY_PAID) {
            throw new FinanceConflictException(
                    "invoice-not-payable",
                    "Facture non payable",
                    "Seule une facture émise ou partiellement payée peut recevoir un encaissement.");
        }
        MoneyMath.requirePositive(paymentAmountXof, "Le montant du paiement");
        if (paymentAmountXof > this.balanceXof) {
            throw new FinanceConflictException(
                    "payment-exceeds-balance",
                    "Montant supérieur au solde",
                    "Le montant encaissé dépasse le solde restant dû de la facture.");
        }
        this.paidAmountXof = MoneyMath.addExact(this.paidAmountXof, paymentAmountXof);
        this.balanceXof = MoneyMath.subtractExact(this.netAmountXof, this.paidAmountXof);
        refreshOpenStatus();
    }

    public void reversePayment(long reversedAmountXof) {
        if (this.status != InvoiceStatus.PARTIALLY_PAID && this.status != InvoiceStatus.PAID) {
            throw new FinanceConflictException(
                    "invoice-reversal-invalid-state",
                    "Contre-passation impossible",
                    "La facture n'est pas dans un état permettant une contre-passation d'encaissement.");
        }
        MoneyMath.requirePositive(reversedAmountXof, "Le montant contre-passé");
        if (reversedAmountXof > this.paidAmountXof) {
            throw new FinanceConflictException(
                    "reversal-exceeds-paid",
                    "Contre-passation incohérente",
                    "Le montant contre-passé dépasse le total encaissé sur la facture.");
        }
        this.paidAmountXof = MoneyMath.subtractExact(this.paidAmountXof, reversedAmountXof);
        this.balanceXof = MoneyMath.subtractExact(this.netAmountXof, this.paidAmountXof);
        refreshOpenStatus();
    }

    public boolean cancel(String reason, Instant cancelledAt, UUID actorId) {
        if (this.status == InvoiceStatus.CANCELLED) {
            throw new FinanceConflictException(
                    "invoice-already-cancelled",
                    "Facture déjà annulée",
                    "Cette facture est déjà annulée.");
        }
        if (this.paidAmountXof > 0L) {
            throw new FinanceConflictException(
                    "invoice-has-payments",
                    "Encaissements actifs présents",
                    "Impossible d'annuler une facture ayant des paiements non contre-passés.");
        }
        boolean wasIssued = this.status != InvoiceStatus.DRAFT;
        this.cancellationReason = requireNonBlank(reason, "Le motif d'annulation est obligatoire.", 255);
        this.cancelledAt = Objects.requireNonNull(cancelledAt, "La date d'annulation est obligatoire.");
        this.cancelledBy = Objects.requireNonNull(actorId, "L'auteur de l'annulation est obligatoire.");
        this.status = InvoiceStatus.CANCELLED;
        return wasIssued;
    }

    public boolean isDraft() {
        return this.status == InvoiceStatus.DRAFT;
    }

    public boolean isIssuedOrActive() {
        return this.status == InvoiceStatus.ISSUED
                || this.status == InvoiceStatus.PARTIALLY_PAID
                || this.status == InvoiceStatus.PAID;
    }

    private void requireDraft(String message) {
        if (this.status != InvoiceStatus.DRAFT) {
            throw new FinanceConflictException("invoice-not-draft", "Facture verrouillée", message);
        }
    }

    private void refreshOpenStatus() {
        if (this.balanceXof == 0L) {
            this.status = InvoiceStatus.PAID;
        } else if (this.paidAmountXof > 0L) {
            this.status = InvoiceStatus.PARTIALLY_PAID;
        } else {
            this.status = InvoiceStatus.ISSUED;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getAcademicYearId() {
        return academicYearId;
    }

    public UUID getEnrollmentId() {
        return enrollmentId;
    }

    public UUID getPayerGuardianId() {
        return payerGuardianId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getTitle() {
        return title;
    }

    public String getCurrency() {
        return currency;
    }

    public long getGrossAmountXof() {
        return grossAmountXof;
    }

    public long getDiscountAmountXof() {
        return discountAmountXof;
    }

    public long getNetAmountXof() {
        return netAmountXof;
    }

    public long getPaidAmountXof() {
        return paidAmountXof;
    }

    public long getBalanceXof() {
        return balanceXof;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public UUID getIssuedBy() {
        return issuedBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public UUID getCancelledBy() {
        return cancelledBy;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    private static String requireNonBlank(String raw, String message, int maxLength) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("Le texte dépasse la longueur maximale autorisée (" + maxLength + ").");
        }
        return trimmed;
    }

    private static String normalizeOptionalText(String raw) {
        return (raw == null || raw.isBlank()) ? null : raw.trim();
    }
}
