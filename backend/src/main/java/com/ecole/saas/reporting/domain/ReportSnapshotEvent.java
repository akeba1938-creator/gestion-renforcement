package com.ecole.saas.reporting.domain;

import com.ecole.saas.reporting.ReportSnapshotEventType;
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
 * Événement chronologique du cycle de vie d'un snapshot de rapport — STRICTEMENT IMMUABLE (ajout seul).
 */
@Entity
@Table(name = "reporting_snapshot_events")
public class ReportSnapshotEvent extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private UUID snapshotId;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 30)
    private ReportSnapshotEventType eventType;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "message", nullable = false, updatable = false, length = 500)
    private String message;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected ReportSnapshotEvent() {
    }

    public ReportSnapshotEvent(
            UUID snapshotId,
            int sequenceNo,
            ReportSnapshotEventType eventType,
            UUID actorId,
            String message,
            Instant occurredAt) {
        this.snapshotId = Objects.requireNonNull(snapshotId, "L'identifiant de snapshot est obligatoire.");
        if (sequenceNo <= 0) {
            throw new ReportingValidationException("Le numéro de séquence d'événement doit être strictement positif.");
        }
        this.sequenceNo = sequenceNo;
        this.eventType = Objects.requireNonNull(eventType, "Le type d'événement est obligatoire.");
        this.actorId = Objects.requireNonNull(actorId, "L'acteur est obligatoire.");
        if (message == null || message.isBlank()) {
            throw new ReportingValidationException("Le message d'événement est obligatoire.");
        }
        String trimmed = message.trim();
        this.message = trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
        this.occurredAt = Objects.requireNonNull(occurredAt, "L'horodatage de l'événement est obligatoire.");
    }

    @Override
    @PreUpdate
    protected void onUpdate() {
        throw new UnsupportedOperationException("Le journal d'événements d'un snapshot est strictement immuable.");
    }

    @PreRemove
    protected void onRemove() {
        throw new UnsupportedOperationException("Les événements d'un snapshot ne peuvent jamais être supprimés.");
    }

    public UUID getId() {
        return id;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public ReportSnapshotEventType getEventType() {
        return eventType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getMessage() {
        return message;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
