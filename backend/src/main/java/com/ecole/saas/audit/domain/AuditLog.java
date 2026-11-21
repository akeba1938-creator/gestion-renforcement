package com.ecole.saas.audit.domain;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditActorType;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditSeverity;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrée du journal d'audit d'un établissement — STRICTEMENT IMMUABLE EN AJOUT SEUL.
 * Isolée par {@code tenant_id} (Hibernate {@code @TenantId} + RLS PostgreSQL forcée fail-closed).
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog extends TenantScopedEntity {

    private static final int MAX_RESOURCE_TYPE_LENGTH = 50;
    private static final int MAX_ACTOR_ROLES_LENGTH = 255;
    private static final int MAX_CORRELATION_ID_LENGTH = 64;

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

    @Column(name = "resource_type", nullable = false, updatable = false, length = 50)
    private String resourceType;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(name = "summary", nullable = false, updatable = false, length = 500)
    private String summary;

    @Column(name = "metadata_json", nullable = false, updatable = false)
    private String metadataJson;

    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditLog() {
    }

    public AuditLog(
            AuditCategory category,
            AuditAction action,
            AuditOutcome outcome,
            AuditSeverity severity,
            AuditActorType actorType,
            UUID actorId,
            String actorRoles,
            String resourceType,
            UUID resourceId,
            String summary,
            Map<String, ?> metadata,
            String correlationId,
            Instant occurredAt) {
        this.category = Objects.requireNonNull(category, "La catégorie d'audit est obligatoire.");
        this.action = Objects.requireNonNull(action, "L'action d'audit est obligatoire.");
        this.outcome = Objects.requireNonNull(outcome, "L'issue de l'action d'audit est obligatoire.");
        this.severity = Objects.requireNonNull(severity, "La sévérité d'audit est obligatoire.");
        this.actorType = Objects.requireNonNull(actorType, "Le type d'acteur d'audit est obligatoire.");
        this.actorId = actorId;
        this.actorRoles = normalizeOptional(actorRoles, MAX_ACTOR_ROLES_LENGTH);
        this.resourceType = normalizeResourceType(resourceType);
        this.resourceId = resourceId;
        this.summary = requireSanitizedSummary(summary);
        this.metadataJson = AuditMetadataSanitizer.toSanitizedJson(metadata);
        this.correlationId = normalizeOptional(correlationId, MAX_CORRELATION_ID_LENGTH);
        this.occurredAt = Objects.requireNonNull(occurredAt, "L'horodatage de l'événement est obligatoire.");
    }

    @Override
    @PreUpdate
    protected void onUpdate() {
        throw new UnsupportedOperationException(
                "Le journal d'audit établissement est strictement immuable : toute modification est interdite.");
    }

    @PreRemove
    protected void onRemove() {
        throw new UnsupportedOperationException(
                "Le journal d'audit établissement est strictement immuable : toute suppression est interdite.");
    }

    public Map<String, String> parsedMetadata() {
        return AuditMetadataSanitizer.parseJson(metadataJson);
    }

    static String normalizeResourceType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AuditValidationException("Le type de ressource d'audit est obligatoire.");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_RESOURCE_TYPE_LENGTH || !normalized.matches("^[A-Z0-9_]{2,50}$")) {
            throw new AuditValidationException(
                    "Le type de ressource d'audit doit contenir entre 2 et 50 caractères alphanumériques ou '_'.");
        }
        return normalized;
    }

    static String requireSanitizedSummary(String raw) {
        String sanitized = AuditMetadataSanitizer.sanitizeText(raw, AuditMetadataSanitizer.MAX_SUMMARY_LENGTH);
        if (sanitized == null || sanitized.isBlank()) {
            throw new AuditValidationException("Le résumé de l'événement d'audit est obligatoire.");
        }
        return sanitized;
    }

    static String normalizeOptional(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String sanitized = AuditMetadataSanitizer.sanitizeText(raw, maxLength);
        return (sanitized == null || sanitized.isBlank()) ? null : sanitized;
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

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getSummary() {
        return summary;
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
}
