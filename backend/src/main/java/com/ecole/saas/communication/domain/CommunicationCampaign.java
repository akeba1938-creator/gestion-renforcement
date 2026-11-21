package com.ecole.saas.communication.domain;

import com.ecole.saas.communication.AudienceType;
import com.ecole.saas.communication.CampaignStatus;
import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.GuardianTargetingMode;
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
@Table(name = "communication_campaigns")
public class CommunicationCampaign extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reference", nullable = false, updatable = false, length = 64)
    private String reference;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, updatable = false, length = 30)
    private CampaignOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, updatable = false, length = 40)
    private MessageCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false, length = 20)
    private CommunicationChannel channel;

    @Column(name = "template_id", updatable = false)
    private UUID templateId;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", nullable = false, updatable = false, length = 40)
    private AudienceType audienceType;

    @Column(name = "audience_target_id", updatable = false)
    private UUID audienceTargetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "guardian_targeting_mode", nullable = false, updatable = false, length = 30)
    private GuardianTargetingMode guardianTargetingMode;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "respect_marketing_opt_out", nullable = false, updatable = false)
    private boolean respectMarketingOptOut;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CampaignStatus status;

    @Column(name = "total_recipients", nullable = false)
    private int totalRecipients;

    @Column(name = "queued_count", nullable = false)
    private int queuedCount;

    @Column(name = "sent_count", nullable = false)
    private int sentCount;

    @Column(name = "delivered_count", nullable = false)
    private int deliveredCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "read_count", nullable = false)
    private int readCount;

    @Column(name = "unit_cost_xof", nullable = false)
    private long unitCostXof;

    @Column(name = "estimated_cost_xof", nullable = false)
    private long estimatedCostXof;

    @Column(name = "actual_cost_xof", nullable = false)
    private long actualCostXof;

    @Column(name = "source_module", updatable = false, length = 40)
    private String sourceModule;

    @Column(name = "source_resource_type", updatable = false, length = 50)
    private String sourceResourceType;

    @Column(name = "source_resource_id", updatable = false)
    private UUID sourceResourceId;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_reason", length = 255)
    private String cancelledReason;

    protected CommunicationCampaign() {
    }

    public CommunicationCampaign(
            String reference,
            String idempotencyKey,
            String requestHash,
            CampaignOrigin origin,
            MessageCategory category,
            CommunicationChannel channel,
            UUID templateId,
            String title,
            AudienceType audienceType,
            UUID audienceTargetId,
            GuardianTargetingMode guardianTargetingMode,
            String subject,
            String body,
            boolean respectMarketingOptOut,
            Instant scheduledAt,
            long unitCostXof,
            String sourceModule,
            String sourceResourceType,
            UUID sourceResourceId,
            UUID createdBy) {
        this.reference = requireNonBlank(reference, 64, "La référence de campagne est obligatoire.");
        this.idempotencyKey = requireNonBlank(idempotencyKey, 128, "La clé d'idempotence est obligatoire.");
        this.requestHash = requireNonBlank(requestHash, 64, "L'empreinte de requête est obligatoire.");
        this.origin = Objects.requireNonNull(origin, "L'origine de la campagne est obligatoire.");
        this.category = Objects.requireNonNull(category, "La catégorie est obligatoire.");
        this.channel = Objects.requireNonNull(channel, "Le canal est obligatoire.");
        this.templateId = templateId;
        this.title = requireNonBlank(title, 150, "Le titre de la campagne est obligatoire.");
        this.audienceType = Objects.requireNonNull(audienceType, "Le type d'audience est obligatoire.");
        if (audienceType.requiresTargetId() && audienceTargetId == null) {
            throw new CommunicationValidationException(
                    "L'identifiant cible (audienceTargetId) est obligatoire pour l'audience " + audienceType + ".");
        }
        if (!audienceType.requiresTargetId() && audienceTargetId != null) {
            throw new CommunicationValidationException(
                    "Aucun identifiant cible ne doit être fourni pour l'audience globale " + audienceType + ".");
        }
        this.audienceTargetId = audienceTargetId;
        this.guardianTargetingMode = guardianTargetingMode != null
                ? guardianTargetingMode
                : GuardianTargetingMode.ALL_ACTIVE;
        this.subject = validateSubject(subject, this.channel);
        this.body = requireNonBlank(body, 4000, "Le contenu du message est obligatoire.");
        this.respectMarketingOptOut = respectMarketingOptOut;
        this.scheduledAt = scheduledAt;
        this.status = scheduledAt != null ? CampaignStatus.SCHEDULED : CampaignStatus.DRAFT;
        if (unitCostXof < 0) {
            throw new CommunicationValidationException("Le coût unitaire en FCFA ne peut pas être négatif.");
        }
        this.unitCostXof = unitCostXof;
        this.estimatedCostXof = 0L;
        this.actualCostXof = 0L;
        this.sourceModule = normalizeOptional(sourceModule, 40);
        this.sourceResourceType = normalizeOptional(sourceResourceType, 50);
        this.sourceResourceId = sourceResourceId;
        this.createdBy = createdBy;
    }

    public void markSending(Instant now) {
        if (this.status != CampaignStatus.DRAFT
                && this.status != CampaignStatus.SCHEDULED
                && this.status != CampaignStatus.QUEUED) {
            throw new CommunicationConflictException(
                    "invalid-campaign-transition",
                    "Impossible de diffuser une campagne au statut " + this.status + ".");
        }
        this.status = CampaignStatus.SENDING;
        this.dispatchedAt = Objects.requireNonNull(now, "L'horodatage d'envoi est obligatoire.");
    }

    public void cancel(String reason, Instant now) {
        if (this.status != CampaignStatus.DRAFT && this.status != CampaignStatus.SCHEDULED) {
            throw new CommunicationConflictException(
                    "invalid-campaign-transition",
                    "Seule une campagne en brouillon ou planifiée peut être annulée (statut actuel : " + this.status + ").");
        }
        this.cancelledReason = requireNonBlank(reason, 255, "Le motif d'annulation est obligatoire.");
        this.cancelledAt = Objects.requireNonNull(now, "L'horodatage d'annulation est obligatoire.");
        this.status = CampaignStatus.CANCELLED;
    }

    public void updateAggregates(
            int totalRecipients,
            int queuedCount,
            int sentCount,
            int deliveredCount,
            int failedCount,
            int readCount,
            long estimatedCostXof,
            long actualCostXof,
            Instant now) {
        this.totalRecipients = totalRecipients;
        this.queuedCount = queuedCount;
        this.sentCount = sentCount;
        this.deliveredCount = deliveredCount;
        this.failedCount = failedCount;
        this.readCount = readCount;
        this.estimatedCostXof = estimatedCostXof;
        this.actualCostXof = actualCostXof;

        if (this.status == CampaignStatus.CANCELLED) {
            return;
        }
        if (totalRecipients == 0) {
            this.status = CampaignStatus.COMPLETED;
            if (this.completedAt == null) {
                this.completedAt = now;
            }
            return;
        }
        if (queuedCount > 0) {
            this.status = CampaignStatus.QUEUED;
            return;
        }
        int succeeded = sentCount + deliveredCount + readCount;
        if (failedCount == 0) {
            this.status = CampaignStatus.COMPLETED;
        } else if (succeeded > 0) {
            this.status = CampaignStatus.PARTIALLY_FAILED;
        } else {
            this.status = CampaignStatus.FAILED;
        }
        if (this.completedAt == null) {
            this.completedAt = now;
        }
    }

    public void setEstimatedMetrics(int totalRecipients, long estimatedCostXof) {
        this.totalRecipients = totalRecipients;
        this.estimatedCostXof = estimatedCostXof;
    }

    private static String validateSubject(String raw, CommunicationChannel channel) {
        String normalized = normalizeOptional(raw, 255);
        if ((channel == CommunicationChannel.EMAIL || channel == CommunicationChannel.IN_APP) && normalized == null) {
            throw new CommunicationValidationException(
                    "L'objet du message est obligatoire pour le canal " + channel + ".");
        }
        return normalized;
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
        if (trimmed.length() > maxLength) {
            throw new CommunicationValidationException(
                    "La valeur dépasse la longueur maximale autorisée de " + maxLength + " caractères.");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public CampaignOrigin getOrigin() {
        return origin;
    }

    public MessageCategory getCategory() {
        return category;
    }

    public CommunicationChannel getChannel() {
        return channel;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public String getTitle() {
        return title;
    }

    public AudienceType getAudienceType() {
        return audienceType;
    }

    public UUID getAudienceTargetId() {
        return audienceTargetId;
    }

    public GuardianTargetingMode getGuardianTargetingMode() {
        return guardianTargetingMode;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public boolean isRespectMarketingOptOut() {
        return respectMarketingOptOut;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public int getTotalRecipients() {
        return totalRecipients;
    }

    public int getQueuedCount() {
        return queuedCount;
    }

    public int getSentCount() {
        return sentCount;
    }

    public int getDeliveredCount() {
        return deliveredCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public int getReadCount() {
        return readCount;
    }

    public long getUnitCostXof() {
        return unitCostXof;
    }

    public long getEstimatedCostXof() {
        return estimatedCostXof;
    }

    public long getActualCostXof() {
        return actualCostXof;
    }

    public String getSourceModule() {
        return sourceModule;
    }

    public String getSourceResourceType() {
        return sourceResourceType;
    }

    public UUID getSourceResourceId() {
        return sourceResourceId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelledReason() {
        return cancelledReason;
    }
}
