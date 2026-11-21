package com.ecole.saas.schooldocs.domain;

import com.ecole.saas.schooldocs.DocumentEventType;
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
 * Événement chronologique du cycle de vie d'un document officiel — STRICTEMENT IMMUABLE EN AJOUT SEUL.
 */
@Entity
@Table(name = "schooldocs_document_events")
public class DocumentEvent extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 30)
    private DocumentEventType eventType;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "reason", updatable = false, length = 500)
    private String reason;

    @Column(name = "related_document_id", updatable = false)
    private UUID relatedDocumentId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected DocumentEvent() {
    }

    public DocumentEvent(
            UUID documentId,
            int sequenceNo,
            DocumentEventType eventType,
            UUID actorId,
            String reason,
            UUID relatedDocumentId,
            Instant occurredAt) {
        this.documentId = Objects.requireNonNull(documentId, "Le document est obligatoire.");
        if (sequenceNo <= 0) {
            throw new SchoolDocValidationException("Le numéro de séquence d'événement doit être strictement positif.");
        }
        this.sequenceNo = sequenceNo;
        this.eventType = Objects.requireNonNull(eventType, "Le type d'événement documentaire est obligatoire.");
        this.actorId = Objects.requireNonNull(actorId, "L'acteur de l'événement est obligatoire.");
        this.reason = normalizeOptional(reason);
        this.relatedDocumentId = relatedDocumentId;
        this.occurredAt = Objects.requireNonNull(occurredAt, "L'horodatage de l'événement est obligatoire.");
    }

    @Override
    @PreUpdate
    protected void onUpdate() {
        throw new UnsupportedOperationException(
                "Le journal des événements documentaires est strictement immuable.");
    }

    @PreRemove
    protected void onRemove() {
        throw new UnsupportedOperationException(
                "Le journal des événements documentaires ne peut jamais être supprimé.");
    }

    private static String normalizeOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public DocumentEventType getEventType() {
        return eventType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getReason() {
        return reason;
    }

    public UUID getRelatedDocumentId() {
        return relatedDocumentId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
