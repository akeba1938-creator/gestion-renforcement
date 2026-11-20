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
 * Encaissement validé — entité STRICTEMENT IMMUABLE (ajout seul).
 * Toute correction ou annulation passe exclusivement par {@link PaymentReversal} et contre-écriture dans {@link LedgerEntry}.
 */
@Entity
@Table(name = "payments")
public class Payment extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "payer_guardian_id", updatable = false)
    private UUID payerGuardianId;

    @Column(name = "requested_schedule_id", updatable = false)
    private UUID requestedScheduleId;

    @Column(name = "amount_xof", nullable = false, updatable = false)
    private long amountXof;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "external_reference", updatable = false, length = 120)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_source", nullable = false, updatable = false, length = 25)
    private PaymentConfirmationSource confirmationSource;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;

    @Column(name = "notes", updatable = false, length = 500)
    private String notes;

    protected Payment() {
    }

    public Payment(
            UUID invoiceId,
            UUID studentId,
            UUID payerGuardianId,
            UUID requestedScheduleId,
            long amountXof,
            PaymentMethod paymentMethod,
            String externalReference,
            PaymentConfirmationSource confirmationSource,
            String idempotencyKey,
            Instant paidAt,
            UUID recordedBy,
            String notes) {
        this.invoiceId = Objects.requireNonNull(invoiceId, "La facture est obligatoire.");
        this.studentId = Objects.requireNonNull(studentId, "L'élève est obligatoire.");
        this.payerGuardianId = payerGuardianId;
        this.requestedScheduleId = requestedScheduleId;
        this.amountXof = MoneyMath.requirePositive(amountXof, "Le montant encaissé");
        this.currency = MoneyMath.CURRENCY_XOF;
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "Le mode de paiement est obligatoire.");
        String normalizedRef = normalizeOptional(externalReference, 120);
        if (paymentMethod.isExternalReferenceRequired() && normalizedRef == null) {
            throw new FinanceValidationException(
                    "payment-reference-required",
                    "Référence obligatoire",
                    "Une référence externe est obligatoire pour le mode de paiement " + paymentMethod + ".");
        }
        this.externalReference = normalizedRef;
        this.confirmationSource = Objects.requireNonNull(
                confirmationSource, "La source de confirmation est obligatoire.");
        this.idempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        this.paidAt = Objects.requireNonNull(paidAt, "La date d'encaissement est obligatoire.");
        this.recordedBy = Objects.requireNonNull(recordedBy, "L'auteur de l'encaissement est obligatoire.");
        this.notes = normalizeOptional(notes, 500);
    }

    public boolean matchesIdempotentRequest(
            UUID invoiceId,
            long amountXof,
            PaymentMethod paymentMethod,
            String externalReference,
            UUID requestedScheduleId) {
        String normalizedRef = normalizeOptional(externalReference, 120);
        return Objects.equals(this.invoiceId, invoiceId)
                && this.amountXof == amountXof
                && this.paymentMethod == paymentMethod
                && Objects.equals(this.externalReference, normalizedRef)
                && Objects.equals(this.requestedScheduleId, requestedScheduleId);
    }

    public static String normalizeIdempotencyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new FinanceValidationException(
                    "idempotency-key-required",
                    "Clé d'idempotence requise",
                    "La clé d'idempotence (Idempotency-Key) est obligatoire pour tout encaissement.");
        }
        String trimmed = rawKey.trim();
        if (trimmed.length() < 8 || trimmed.length() > 128) {
            throw new FinanceValidationException(
                    "idempotency-key-invalid",
                    "Clé d'idempotence invalide",
                    "La clé d'idempotence doit contenir entre 8 et 128 caractères.");
        }
        return trimmed;
    }

    public static String normalizeOptional(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("La valeur dépasse la longueur maximale autorisée (" + maxLength + ").");
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

    public UUID getPayerGuardianId() {
        return payerGuardianId;
    }

    public UUID getRequestedScheduleId() {
        return requestedScheduleId;
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

    public String getExternalReference() {
        return externalReference;
    }

    public PaymentConfirmationSource getConfirmationSource() {
        return confirmationSource;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public String getNotes() {
        return notes;
    }
}
