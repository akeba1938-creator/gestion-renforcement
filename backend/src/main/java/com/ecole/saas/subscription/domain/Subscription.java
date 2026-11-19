package com.ecole.saas.subscription.domain;

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

@Entity
@Table(name = "subscriptions")
public class Subscription extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    @Column(name = "amount_xof", nullable = false)
    private long amountXof;

    @Column(name = "max_students", nullable = false)
    private int maxStudents;

    @Column(name = "max_users", nullable = false)
    private int maxUsers;

    @Column(name = "max_storage_mb", nullable = false)
    private int maxStorageMb;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    protected Subscription() {
    }

    public Subscription(
            UUID planId,
            SubscriptionStatus status,
            BillingCycle billingCycle,
            long amountXof,
            int maxStudents,
            int maxUsers,
            int maxStorageMb,
            Instant startsAt,
            Instant endsAt) {
        this.planId = Objects.requireNonNull(planId, "L'identifiant du forfait est obligatoire.");
        this.status = Objects.requireNonNull(status, "Le statut de l'abonnement est obligatoire.");
        this.billingCycle = Objects.requireNonNull(billingCycle, "Le cycle de facturation est obligatoire.");
        if (amountXof < 0) {
            throw new IllegalArgumentException("Le montant en FCFA doit être positif ou nul.");
        }
        if (maxStudents <= 0 || maxUsers <= 0 || maxStorageMb <= 0) {
            throw new IllegalArgumentException("Les quotas d'abonnement doivent être strictement positifs.");
        }
        this.startsAt = Objects.requireNonNull(startsAt, "La date de début est obligatoire.");
        this.endsAt = Objects.requireNonNull(endsAt, "La date de fin est obligatoire.");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début.");
        }
        this.amountXof = amountXof;
        this.maxStudents = maxStudents;
        this.maxUsers = maxUsers;
        this.maxStorageMb = maxStorageMb;
    }

    public void updateStatus(SubscriptionStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "Le statut de l'abonnement est obligatoire.");
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public long getAmountXof() {
        return amountXof;
    }

    public int getMaxStudents() {
        return maxStudents;
    }

    public int getMaxUsers() {
        return maxUsers;
    }

    public int getMaxStorageMb() {
        return maxStorageMb;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }
}
