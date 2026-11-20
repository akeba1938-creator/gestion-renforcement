package com.ecole.saas.finance.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * Imputation immuable (ajout seul) d'une fraction ou de la totalité d'un paiement sur une échéance.
 */
@Entity
@Table(name = "payment_allocations")
public class PaymentAllocation extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "schedule_id", nullable = false, updatable = false)
    private UUID scheduleId;

    @Column(name = "amount_xof", nullable = false, updatable = false)
    private long amountXof;

    protected PaymentAllocation() {
    }

    public PaymentAllocation(UUID paymentId, UUID scheduleId, long amountXof) {
        this.paymentId = Objects.requireNonNull(paymentId, "L'identifiant du paiement est obligatoire.");
        this.scheduleId = Objects.requireNonNull(scheduleId, "L'identifiant de l'échéance est obligatoire.");
        this.amountXof = MoneyMath.requirePositive(amountXof, "Le montant imputé");
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getScheduleId() {
        return scheduleId;
    }

    public long getAmountXof() {
        return amountXof;
    }
}
