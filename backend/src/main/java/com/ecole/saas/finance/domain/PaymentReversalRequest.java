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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Demande de contre-passation (annulation tracée) d'un encaissement validé, soumise à approbation.
 */
@Entity
@Table(name = "payment_reversal_requests")
public class PaymentReversalRequest extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "amount_xof", nullable = false, updatable = false)
    private long amountXof;

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

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PaymentReversalRequest() {
    }

    public PaymentReversalRequest(
            UUID paymentId,
            UUID invoiceId,
            UUID studentId,
            long amountXof,
            String reason,
            UUID requestedBy,
            Instant requestedAt) {
        this.paymentId = Objects.requireNonNull(paymentId, "Le paiement est obligatoire.");
        this.invoiceId = Objects.requireNonNull(invoiceId, "La facture est obligatoire.");
        this.studentId = Objects.requireNonNull(studentId, "L'élève est obligatoire.");
        this.amountXof = MoneyMath.requirePositive(amountXof, "Le montant à contre-passer");
        this.reason = requireReason(reason, "Le motif de contre-passation est obligatoire.");
        this.status = ApprovalStatus.PENDING_APPROVAL;
        this.requestedBy = Objects.requireNonNull(requestedBy, "L'auteur de la demande est obligatoire.");
        this.requestedAt = Objects.requireNonNull(requestedAt, "La date de demande est obligatoire.");
    }

    public void approve(UUID actorId, Instant decidedAt, String decisionNote) {
        requirePending();
        UUID validatedActor = Objects.requireNonNull(actorId, "Le valideur est obligatoire.");
        if (validatedActor.equals(this.requestedBy)) {
            throw new FinanceForbiddenException(
                    "L'auteur d'une demande de contre-passation ne peut pas l'approuver lui-même.");
        }
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

    private void requirePending() {
        if (this.status != ApprovalStatus.PENDING_APPROVAL) {
            throw new FinanceConflictException(
                    "reversal-request-already-decided",
                    "Demande déjà traitée",
                    "Cette demande de contre-passation a déjà été traitée.");
        }
    }

    private static String requireReason(String raw, String message) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("Le texte dépasse la longueur maximale autorisée (500).");
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

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public long getAmountXof() {
        return amountXof;
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

    public long getVersion() {
        return version;
    }
}
