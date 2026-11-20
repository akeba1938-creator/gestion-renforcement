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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Échéance d'un échéancier de paiement rattachée à une facture élève.
 */
@Entity
@Table(name = "payment_schedules")
public class PaymentSchedule extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "installment_no", nullable = false)
    private int installmentNo;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount_xof", nullable = false)
    private long amountXof;

    @Column(name = "paid_amount_xof", nullable = false)
    private long paidAmountXof;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InstallmentStatus status;

    protected PaymentSchedule() {
    }

    public PaymentSchedule(UUID invoiceId, int installmentNo, String label, LocalDate dueDate, long amountXof) {
        this.invoiceId = Objects.requireNonNull(invoiceId, "L'identifiant de la facture est obligatoire.");
        if (installmentNo <= 0) {
            throw new IllegalArgumentException("Le numéro d'échéance doit être strictement positif.");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Le libellé d'échéance est obligatoire.");
        }
        this.installmentNo = installmentNo;
        this.label = label.trim();
        this.dueDate = Objects.requireNonNull(dueDate, "La date d'échéance est obligatoire.");
        this.amountXof = MoneyMath.requireNonNegative(amountXof, "Le montant d'échéance");
        this.paidAmountXof = 0L;
        this.status = this.amountXof == 0L ? InstallmentStatus.PAID : InstallmentStatus.PENDING;
    }

    public long remainingAmountXof() {
        if (this.status == InstallmentStatus.CANCELLED) {
            return 0L;
        }
        return MoneyMath.subtractExact(this.amountXof, this.paidAmountXof);
    }

    public void applyPayment(long allocatedAmountXof) {
        if (this.status == InstallmentStatus.CANCELLED) {
            throw new FinanceConflictException(
                    "schedule-cancelled",
                    "Échéance annulée",
                    "Impossible d'imputer un paiement sur une échéance annulée.");
        }
        MoneyMath.requirePositive(allocatedAmountXof, "Le montant imputé");
        if (allocatedAmountXof > remainingAmountXof()) {
            throw new FinanceConflictException(
                    "schedule-overpayment",
                    "Dépassement d'échéance",
                    "Le montant imputé dépasse le solde restant de l'échéance.");
        }
        this.paidAmountXof = MoneyMath.addExact(this.paidAmountXof, allocatedAmountXof);
        refreshStatus();
    }

    public void reversePayment(long reversedAmountXof) {
        if (this.status == InstallmentStatus.CANCELLED) {
            throw new FinanceConflictException(
                    "schedule-cancelled",
                    "Échéance annulée",
                    "Impossible de contre-passer un paiement sur une échéance annulée.");
        }
        MoneyMath.requirePositive(reversedAmountXof, "Le montant contre-passé");
        if (reversedAmountXof > this.paidAmountXof) {
            throw new FinanceConflictException(
                    "schedule-reversal-exceeds-paid",
                    "Contre-passation incohérente",
                    "Le montant contre-passé dépasse le montant payé sur l'échéance.");
        }
        this.paidAmountXof = MoneyMath.subtractExact(this.paidAmountXof, reversedAmountXof);
        refreshStatus();
    }

    public long reduceByAdjustment(long maxReductionXof) {
        if (this.status == InstallmentStatus.CANCELLED || maxReductionXof <= 0L) {
            return 0L;
        }
        long available = remainingAmountXof();
        long applied = Math.min(available, maxReductionXof);
        if (applied > 0L) {
            this.amountXof = MoneyMath.subtractExact(this.amountXof, applied);
            refreshStatus();
        }
        return applied;
    }

    public void cancel() {
        if (this.paidAmountXof > 0L) {
            throw new FinanceConflictException(
                    "schedule-has-payments",
                    "Échéance déjà payée",
                    "Impossible d'annuler une échéance comportant des paiements actifs.");
        }
        this.status = InstallmentStatus.CANCELLED;
    }

    public boolean isOverdueAt(LocalDate referenceDate) {
        Objects.requireNonNull(referenceDate, "La date de référence est obligatoire.");
        return (this.status == InstallmentStatus.PENDING || this.status == InstallmentStatus.PARTIALLY_PAID)
                && remainingAmountXof() > 0L
                && this.dueDate.isBefore(referenceDate);
    }

    private void refreshStatus() {
        if (this.paidAmountXof == this.amountXof) {
            this.status = InstallmentStatus.PAID;
        } else if (this.paidAmountXof > 0L) {
            this.status = InstallmentStatus.PARTIALLY_PAID;
        } else {
            this.status = InstallmentStatus.PENDING;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public int getInstallmentNo() {
        return installmentNo;
    }

    public String getLabel() {
        return label;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public long getAmountXof() {
        return amountXof;
    }

    public long getPaidAmountXof() {
        return paidAmountXof;
    }

    public InstallmentStatus getStatus() {
        return status;
    }
}
