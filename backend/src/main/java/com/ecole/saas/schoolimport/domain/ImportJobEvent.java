package com.ecole.saas.schoolimport.domain;

import com.ecole.saas.schoolimport.ImportJobEventType;
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
 * Journal chronologique strictement immuable (ajout seul) des événements d'un lot d'import.
 */
@Entity
@Table(name = "schoolimport_job_events")
public class ImportJobEvent extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 30)
    private ImportJobEventType eventType;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "message", nullable = false, updatable = false, length = 500)
    private String message;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected ImportJobEvent() {
    }

    public ImportJobEvent(
            UUID jobId,
            int sequenceNo,
            ImportJobEventType eventType,
            UUID actorId,
            String message,
            Instant occurredAt) {
        this.jobId = Objects.requireNonNull(jobId, "L'identifiant du lot est obligatoire.");
        if (sequenceNo <= 0) {
            throw new SchoolImportValidationException("Le numéro de séquence d'événement doit être positif.");
        }
        this.sequenceNo = sequenceNo;
        this.eventType = Objects.requireNonNull(eventType, "Le type d'événement est obligatoire.");
        this.actorId = Objects.requireNonNull(actorId, "L'acteur est obligatoire.");
        if (message == null || message.isBlank()) {
            throw new SchoolImportValidationException("Le message d'événement est obligatoire.");
        }
        String trimmed = message.trim();
        this.message = trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
        this.occurredAt = Objects.requireNonNull(occurredAt, "L'horodatage d'événement est obligatoire.");
    }

    @Override
    @PreUpdate
    protected void onUpdate() {
        throw new UnsupportedOperationException("Le journal d'événements d'import est strictement immuable.");
    }

    @PreRemove
    protected void onRemove() {
        throw new UnsupportedOperationException("Le journal d'événements d'import ne peut jamais être supprimé.");
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public ImportJobEventType getEventType() {
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
