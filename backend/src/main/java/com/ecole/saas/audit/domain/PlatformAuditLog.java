package com.ecole.saas.audit.domain;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditActorType;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrée du journal d'audit plateforme ({@code ADMIN_SAAS}) — STRICTEMENT IMMUABLE EN AJOUT SEUL
 * et séparée des données d'établissement (RLS PostgreSQL interdisant tout accès depuis un contexte tenant).
 */
@Entity
@Table(name = "platform_audit_logs")
public class PlatformAuditLog {

    private static final int MAX_ACTOR_ROLES_LENGTH = 255;
    private static final int MAX_CORRELATION_ID_LENGTH = 64;
    private static final int MAX_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, updatable = false, length = 30)
    private AuditCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 64)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 20)
    private AuditOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, updatable = false, length = 20)
    private AuditSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 20)
    private AuditActorType actorType;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_roles", updatable = false, length = 255)
    private String actorRoles;

    @Column(name = "target_school_id", updatable = false)
    private UUID targetSchoolId;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 50)
    private String resourceType;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(name = "summary", nullable = false, updatable = false, length = 500)
    private String summary;

    @Column(name = "reason", updatable = false, length = 500)
    private String reason;

    @Column(name = "metadata_json", nullable = false, updatable = false)
    private String metadataJson;

    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlatformAuditLog() {
    }

    public PlatformAuditLog(
            AuditCategory category,
            AuditAction action,
            AuditOutcome outcome,
            AuditSeverity severity,
            AuditActorType actorType,
            UUID actorId,
            String actorRoles,
            UUID targetSchoolId,
            String resourceType,
            UUID resourceId,
            String summary,
            String reason,
            Map<String, ?> metadata,
            String correlationId,
            Instant occurredAt) {
        this.category = Objects.requireNonNull(category, "La catégorie d'audit est obligatoire.");
        this.action = Objects.requireNonNull(action, "L'action d'audit est obligatoire.");
        this.outcome = Objects.requireNonNull(outcome, "L'issue de l'action d'audit est obligatoire.");
        this.severity = Objects.requireNonNull(severity, "La sévérité d'audit est obligatoire.");
        this.actorType = Objects.requireNonNull(actorType, "Le type d'acteur d'audit est obligatoire.");
        this.actorId = actorId;
        this.actorRoles = AuditLog.normalizeOptional(actorRoles, MAX_ACTOR_ROLES_LENGTH);
        this.targetSchoolId = targetSchoolId;
        this.resourceType = AuditLog.normalizeResourceType(resourceType);
        this.resourceId = resourceId;
        this.summary = AuditLog.requireSanitizedSummary(summary);
        this.reason = AuditLog.normalizeOptional(reason, MAX_REASON_LENGTH);
        this.metadataJson = AuditMetadataSanitizer.toSanitizedJson(metadata);
        this.correlationId = AuditLog.normalizeOptional(correlationId, MAX_CORRELATION_ID_LENGTH);
        this.occurredAt = Objects.requireNonNull(occurredAt, "L'horodatage de l'événement est obligatoire.");
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        throw new UnsupportedOperationException(
                "Le journal d'audit plateforme est strictement immuable : toute modification est interdite.");
    }

    @PreRemove
    protected void onRemove() {
        throw new UnsupportedOperationException(
                "Le journal d'audit plateforme est strictement immuable : toute suppression est interdite.");
    }

    public Map<String, String> parsedMetadata() {
        return AuditMetadataSanitizer.parseJson(metadataJson);
    }

    public UUID getId() {
        return id;
    }

    public AuditCategory getCategory() {
        return category;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public AuditSeverity getSeverity() {
        return severity;
    }

    public AuditActorType getActorType() {
        return actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorRoles() {
        return actorRoles;
    }

    public UUID getTargetSchoolId() {
        return targetSchoolId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getSummary() {
        return summary;
    }

    public String getReason() {
        return reason;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
