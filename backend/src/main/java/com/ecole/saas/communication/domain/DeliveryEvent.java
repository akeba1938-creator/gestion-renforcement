package com.ecole.saas.communication.domain;

import com.ecole.saas.communication.DeliveryStatus;
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
 * Événement immuable en ajout seul retraçant chaque transition de statut d'une {@link MessageDelivery}.
 */
@Entity
@Table(name = "communication_delivery_events")
public class DeliveryEvent extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", updatable = false, length = 20)
    private DeliveryStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, updatable = false, length = 20)
    private DeliveryStatus newStatus;

    @Column(name = "provider_name", updatable = false, length = 60)
    private String providerName;

    @Column(name = "provider_status", updatable = false, length = 60)
    private String providerStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", updatable = false, length = 40)
    private DeliveryFailureCode failureCode;

    @Column(name = "detail", updatable = false, length = 500)
    private String detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected DeliveryEvent() {
    }

    public DeliveryEvent(
            UUID deliveryId,
            int sequenceNo,
            DeliveryStatus previousStatus,
            DeliveryStatus newStatus,
            String providerName,
            String providerStatus,
            DeliveryFailureCode failureCode,
            String detail,
            Instant occurredAt) {
        this.deliveryId = Objects.requireNonNull(deliveryId, "L'identifiant de remise est obligatoire.");
        if (sequenceNo <= 0) {
            throw new CommunicationValidationException("Le numéro de séquence d'événement doit être strictement positif.");
        }
        this.sequenceNo = sequenceNo;
        this.previousStatus = previousStatus;
        this.newStatus = Objects.requireNonNull(newStatus, "Le nouveau statut de remise est obligatoire.");
        this.providerName = trimToNull(providerName, 60);
        this.providerStatus = trimToNull(providerStatus, 60);
        this.failureCode = failureCode;
        this.detail = trimToNull(detail, 500);
        this.occurredAt = Objects.requireNonNull(occurredAt, "L'horodatage de l'événement est obligatoire.");
    }

    @Override
    @PreUpdate
    protected void onUpdate() {
        throw new UnsupportedOperationException(
                "Le journal des événements de remise est strictement immuable : toute modification est interdite.");
    }

    @PreRemove
    protected void onRemove() {
        throw new UnsupportedOperationException(
                "Le journal des événements de remise est strictement immuable : toute suppression est interdite.");
    }

    private static String trimToNull(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDeliveryId() {
        return deliveryId;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public DeliveryStatus getPreviousStatus() {
        return previousStatus;
    }

    public DeliveryStatus getNewStatus() {
        return newStatus;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public DeliveryFailureCode getFailureCode() {
        return failureCode;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
