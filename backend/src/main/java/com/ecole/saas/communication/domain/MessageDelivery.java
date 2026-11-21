package com.ecole.saas.communication.domain;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.DeliveryStatus;
import com.ecole.saas.communication.MessageCategory;
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
@Table(name = "communication_deliveries")
public class MessageDelivery extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, updatable = false, length = 20)
    private RecipientType recipientType;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Column(name = "recipient_user_id", updatable = false)
    private UUID recipientUserId;

    @Column(name = "related_student_id", updatable = false)
    private UUID relatedStudentId;

    @Column(name = "recipient_display_name", nullable = false, updatable = false, length = 200)
    private String recipientDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, updatable = false, length = 40)
    private MessageCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false, length = 20)
    private CommunicationChannel channel;

    @Column(name = "destination_address", length = 255)
    private String destinationAddress;

    @Column(name = "rendered_subject", length = 255)
    private String renderedSubject;

    @Column(name = "rendered_body", nullable = false)
    private String renderedBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "provider_name", length = 60)
    private String providerName;

    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 40)
    private DeliveryFailureCode failureCode;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "unit_cost_xof", nullable = false)
    private long unitCostXof;

    @Column(name = "charged_cost_xof", nullable = false)
    private long chargedCostXof;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected MessageDelivery() {
    }

    public MessageDelivery(
            UUID campaignId,
            RecipientType recipientType,
            UUID recipientId,
            UUID recipientUserId,
            UUID relatedStudentId,
            String recipientDisplayName,
            MessageCategory category,
            CommunicationChannel channel,
            String destinationAddress,
            String renderedSubject,
            String renderedBody,
            long unitCostXof,
            Instant queuedAt) {
        this.campaignId = Objects.requireNonNull(campaignId, "L'identifiant de campagne est obligatoire.");
        this.recipientType = Objects.requireNonNull(recipientType, "Le type de destinataire est obligatoire.");
        this.recipientId = Objects.requireNonNull(recipientId, "L'identifiant du destinataire est obligatoire.");
        this.recipientUserId = recipientUserId;
        this.relatedStudentId = relatedStudentId;
        this.recipientDisplayName = requireNonBlank(recipientDisplayName, 200, "Le nom du destinataire est obligatoire.");
        this.category = Objects.requireNonNull(category, "La catégorie est obligatoire.");
        this.channel = Objects.requireNonNull(channel, "Le canal est obligatoire.");
        this.destinationAddress = normalizeOptional(destinationAddress, 255);
        this.renderedSubject = normalizeOptional(renderedSubject, 255);
        this.renderedBody = requireNonBlank(renderedBody, 4000, "Le corps rendu du message est obligatoire.");
        if (unitCostXof < 0) {
            throw new CommunicationValidationException("Le coût unitaire en FCFA ne peut pas être négatif.");
        }
        this.unitCostXof = unitCostXof;
        this.chargedCostXof = 0L;
        this.status = DeliveryStatus.QUEUED;
        this.attemptCount = 0;
        this.queuedAt = Objects.requireNonNull(queuedAt, "L'horodatage de mise en file est obligatoire.");
    }

    public DeliveryStatus markSent(
            String providerName,
            String providerMessageId,
            long chargedCostXof,
            Instant at) {
        DeliveryStatus previous = this.status;
        DeliveryStatusMachine.assertTransitionAllowed(this.status, DeliveryStatus.SENT);
        if (chargedCostXof < 0) {
            throw new CommunicationValidationException("Le coût facturé en FCFA ne peut pas être négatif.");
        }
        this.providerName = normalizeOptional(providerName, 60);
        this.providerMessageId = normalizeOptional(providerMessageId, 120);
        this.chargedCostXof = chargedCostXof;
        this.failureCode = null;
        this.failureReason = null;
        this.attemptCount++;
        this.sentAt = Objects.requireNonNull(at, "L'horodatage d'envoi est obligatoire.");
        this.status = DeliveryStatus.SENT;
        return previous;
    }

    public DeliveryStatus markDelivered(
            String providerName,
            String providerMessageId,
            long chargedCostXof,
            Instant at) {
        DeliveryStatus previous = this.status;
        DeliveryStatusMachine.assertTransitionAllowed(this.status, DeliveryStatus.DELIVERED);
        if (chargedCostXof < 0) {
            throw new CommunicationValidationException("Le coût facturé en FCFA ne peut pas être négatif.");
        }
        if (providerName != null && !providerName.isBlank()) {
            this.providerName = normalizeOptional(providerName, 60);
        }
        if (providerMessageId != null && !providerMessageId.isBlank()) {
            this.providerMessageId = normalizeOptional(providerMessageId, 120);
        }
        if (previous == DeliveryStatus.QUEUED) {
            this.attemptCount++;
            this.sentAt = at;
            this.chargedCostXof = chargedCostXof;
        } else if (chargedCostXof > 0 && this.chargedCostXof == 0) {
            this.chargedCostXof = chargedCostXof;
        }
        this.failureCode = null;
        this.failureReason = null;
        this.deliveredAt = Objects.requireNonNull(at, "L'horodatage de remise est obligatoire.");
        this.status = DeliveryStatus.DELIVERED;
        return previous;
    }

    public DeliveryStatus markFailed(
            String providerName,
            DeliveryFailureCode failureCode,
            String failureReason,
            boolean incrementAttempt,
            Instant at) {
        DeliveryStatus previous = this.status;
        DeliveryStatusMachine.assertTransitionAllowed(this.status, DeliveryStatus.FAILED);
        if (providerName != null && !providerName.isBlank()) {
            this.providerName = normalizeOptional(providerName, 60);
        }
        this.failureCode = Objects.requireNonNull(failureCode, "Le code d'échec est obligatoire.");
        this.failureReason = normalizeOptional(failureReason, 500);
        if (incrementAttempt) {
            this.attemptCount++;
        }
        this.failedAt = Objects.requireNonNull(at, "L'horodatage d'échec est obligatoire.");
        this.status = DeliveryStatus.FAILED;
        return previous;
    }

    public DeliveryStatus markRead(Instant at) {
        DeliveryStatus previous = this.status;
        if (this.status == DeliveryStatus.READ) {
            return previous;
        }
        DeliveryStatusMachine.assertTransitionAllowed(this.status, DeliveryStatus.READ);
        Instant timestamp = Objects.requireNonNull(at, "L'horodatage de lecture est obligatoire.");
        if (this.deliveredAt == null) {
            this.deliveredAt = timestamp;
        }
        this.readAt = timestamp;
        this.status = DeliveryStatus.READ;
        return previous;
    }

    public DeliveryStatus requeueForRetry(Instant at) {
        DeliveryStatus previous = this.status;
        DeliveryStatusMachine.assertTransitionAllowed(this.status, DeliveryStatus.QUEUED);
        if (this.failureCode == null || !this.failureCode.isRetryable()) {
            throw new CommunicationConflictException(
                    "delivery-not-retryable",
                    "Cet échec (" + this.failureCode + ") n'est pas éligible à une relance technique.");
        }
        this.failureCode = null;
        this.failureReason = null;
        this.failedAt = null;
        this.queuedAt = Objects.requireNonNull(at, "L'horodatage de relance est obligatoire.");
        this.status = DeliveryStatus.QUEUED;
        return previous;
    }

    public boolean isRetryableFailure() {
        return this.status == DeliveryStatus.FAILED
                && this.failureCode != null
                && this.failureCode.isRetryable();
    }

    private static String requireNonBlank(String raw, int maxLength, String message) {
        if (raw == null || raw.isBlank()) {
            throw new CommunicationValidationException(message);
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new CommunicationValidationException(
                    "La valeur dépasse la longueur maximale autorisée de " + maxLength + " caractères.");
        }
        return trimmed;
    }

    private static String normalizeOptional(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public RecipientType getRecipientType() {
        return recipientType;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public UUID getRelatedStudentId() {
        return relatedStudentId;
    }

    public String getRecipientDisplayName() {
        return recipientDisplayName;
    }

    public MessageCategory getCategory() {
        return category;
    }

    public CommunicationChannel getChannel() {
        return channel;
    }

    public String getDestinationAddress() {
        return destinationAddress;
    }

    public String getRenderedSubject() {
        return renderedSubject;
    }

    public String getRenderedBody() {
        return renderedBody;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public DeliveryFailureCode getFailureCode() {
        return failureCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public long getUnitCostXof() {
        return unitCostXof;
    }

    public long getChargedCostXof() {
        return chargedCostXof;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
