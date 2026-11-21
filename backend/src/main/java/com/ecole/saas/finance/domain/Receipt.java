package com.ecole.saas.finance.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Reçu de paiement séquentiel sans trou et jeton QR opaque — STRICTEMENT IMMUABLE (ajout seul).
 */
@Entity
@Table(name = "receipts")
public class Receipt extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "receipt_number", nullable = false, updatable = false, length = 64)
    private String receiptNumber;

    @Column(name = "verification_token", nullable = false, updatable = false, length = 128)
    private String verificationToken;

    @Column(name = "amount_xof", nullable = false, updatable = false)
    private long amountXof;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "issued_by", nullable = false, updatable = false)
    private UUID issuedBy;

    protected Receipt() {
    }

    public Receipt(
            UUID paymentId,
            UUID invoiceId,
            UUID studentId,
            String receiptNumber,
            String verificationToken,
            long amountXof,
            PaymentMethod paymentMethod,
            Instant issuedAt,
            UUID issuedBy) {
        this.paymentId = Objects.requireNonNull(paymentId, "Le paiement est obligatoire.");
        this.invoiceId = Objects.requireNonNull(invoiceId, "La facture est obligatoire.");
        this.studentId = Objects.requireNonNull(studentId, "L'élève est obligatoire.");
        if (receiptNumber == null || receiptNumber.isBlank()) {
            throw new IllegalArgumentException("Le numéro de reçu est obligatoire.");
        }
        if (verificationToken == null || verificationToken.isBlank()) {
            throw new IllegalArgumentException("Le jeton opaque de vérification est obligatoire.");
        }
        this.receiptNumber = receiptNumber.trim();
        this.verificationToken = verificationToken.trim();
        this.amountXof = MoneyMath.requirePositive(amountXof, "Le montant du reçu");
        this.currency = MoneyMath.CURRENCY_XOF;
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "Le mode de paiement est obligatoire.");
        this.issuedAt = Objects.requireNonNull(issuedAt, "La date d'émission du reçu est obligatoire.");
        this.issuedBy = Objects.requireNonNull(issuedBy, "L'émetteur du reçu est obligatoire.");
    }

    @Override
    @PreUpdate
    protected void onUpdate() {
        throw new UnsupportedOperationException("Les reçus de paiement sont strictement immuables.");
    }

    @PreRemove
    protected void onRemove() {
        throw new UnsupportedOperationException("Les reçus de paiement ne peuvent jamais être supprimés.");
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

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public long getAmountXof() {
        return amountXof;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public UUID getIssuedBy() {
        return issuedBy;
    }
}
