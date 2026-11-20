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
 * Contre-passation validée d'un encaissement — entité STRICTEMENT IMMUABLE (ajout seul).
 * L'encaissement original dans {@code payments} et son reçu dans {@code receipts} ne sont jamais modifiés ni supprimés.
 */
@Entity
@Table(name = "payment_reversals")
public class PaymentReversal extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "original_payment_id", nullable = false, updatable = false)
    private UUID originalPaymentId;

    @Column(name = "reversal_request_id", nullable = false, updatable = false)
    private UUID reversalRequestId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "amount_xof", nullable = false, updatable = false)
    private long amountXof;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "approved_by", nullable = false, updatable = false)
    private UUID approvedBy;

    @Column(name = "reversed_at", nullable = false, updatable = false)
    private Instant reversedAt;

    protected PaymentReversal() {
    }

    public PaymentReversal(
            UUID originalPaymentId,
            UUID reversalRequestId,
            UUID invoiceId,
            UUID studentId,
            long amountXof,
            PaymentMethod paymentMethod,
            String reason,
            UUID approvedBy,
            Instant reversedAt) {
        this.originalPaymentId = Objects.requireNonNull(originalPaymentId, "Le paiement d'origine est obligatoire.");
        this.reversalRequestId = Objects.requireNonNull(reversalRequestId, "La demande de contre-passation est obligatoire.");
        this.invoiceId = Objects.requireNonNull(invoiceId, "La facture est obligatoire.");
        this.studentId = Objects.requireNonNull(studentId, "L'élève est obligatoire.");
        this.amountXof = MoneyMath.requirePositive(amountXof, "Le montant contre-passé");
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "Le mode de paiement est obligatoire.");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Le motif de contre-passation est obligatoire.");
        }
        this.reason = reason.trim();
        this.approvedBy = Objects.requireNonNull(approvedBy, "Le valideur est obligatoire.");
        this.reversedAt = Objects.requireNonNull(reversedAt, "La date de contre-passation est obligatoire.");
    }

    public UUID getId() {
        return id;
    }

    public UUID getOriginalPaymentId() {
        return originalPaymentId;
    }

    public UUID getReversalRequestId() {
        return reversalRequestId;
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

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getReason() {
        return reason;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }
}
