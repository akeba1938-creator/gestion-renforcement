package com.ecole.saas.subscription.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import com.ecole.saas.subscription.EffectiveQuotas;
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
 * Événement immuable (ajout seul) traçant le cycle de vie d'un abonnement par établissement.
 */
@Entity
@Table(name = "subscription_events")
public class SubscriptionEvent extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 40)
    private SubscriptionEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", updatable = false, length = 20)
    private SubscriptionStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, updatable = false, length = 20)
    private SubscriptionStatus newStatus;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "amount_xof", nullable = false, updatable = false)
    private long amountXof;

    @Column(name = "effective_max_students", nullable = false, updatable = false)
    private int effectiveMaxStudents;

    @Column(name = "effective_max_users", nullable = false, updatable = false)
    private int effectiveMaxUsers;

    @Column(name = "effective_max_storage_mb", nullable = false, updatable = false)
    private int effectiveMaxStorageMb;

    @Column(name = "reason", updatable = false, length = 255)
    private String reason;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected SubscriptionEvent() {
    }

    public SubscriptionEvent(
            UUID subscriptionId,
            int sequenceNo,
            SubscriptionEventType eventType,
            SubscriptionStatus previousStatus,
            SubscriptionStatus newStatus,
            UUID planId,
            long amountXof,
            EffectiveQuotas effectiveQuotas,
            String reason,
            UUID actorId,
            Instant occurredAt) {
        this.subscriptionId = Objects.requireNonNull(subscriptionId, "L'identifiant d'abonnement est obligatoire.");
        if (sequenceNo <= 0) {
            throw new IllegalArgumentException("Le numéro de séquence doit être strictement positif.");
        }
        this.sequenceNo = sequenceNo;
        this.eventType = Objects.requireNonNull(eventType, "Le type d'événement est obligatoire.");
        this.previousStatus = previousStatus;
        this.newStatus = Objects.requireNonNull(newStatus, "Le nouveau statut est obligatoire.");
        this.planId = Objects.requireNonNull(planId, "L'identifiant du forfait est obligatoire.");
        if (amountXof < 0) {
            throw new IllegalArgumentException("Le montant en FCFA doit être positif ou nul.");
        }
        Objects.requireNonNull(effectiveQuotas, "Les quotas effectifs sont obligatoires.");
        this.amountXof = amountXof;
        this.effectiveMaxStudents = effectiveQuotas.maxStudents();
        this.effectiveMaxUsers = effectiveQuotas.maxUsers();
        this.effectiveMaxStorageMb = effectiveQuotas.maxStorageMb();
        this.reason = (reason == null || reason.isBlank()) ? null : reason.trim();
        this.actorId = actorId;
        this.occurredAt = Objects.requireNonNull(occurredAt, "La date d'événement est obligatoire.");
    }

    @Override
    @PreUpdate
    protected void onUpdate() {
        throw new UnsupportedOperationException(
                "Le journal des événements d'abonnement est strictement immuable : toute modification est interdite.");
    }

    @PreRemove
    protected void onRemove() {
        throw new UnsupportedOperationException(
                "Le journal des événements d'abonnement est strictement immuable : toute suppression est interdite.");
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public SubscriptionEventType getEventType() {
        return eventType;
    }

    public SubscriptionStatus getPreviousStatus() {
        return previousStatus;
    }

    public SubscriptionStatus getNewStatus() {
        return newStatus;
    }

    public UUID getPlanId() {
        return planId;
    }

    public long getAmountXof() {
        return amountXof;
    }

    public int getEffectiveMaxStudents() {
        return effectiveMaxStudents;
    }

    public int getEffectiveMaxUsers() {
        return effectiveMaxUsers;
    }

    public int getEffectiveMaxStorageMb() {
        return effectiveMaxStorageMb;
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
