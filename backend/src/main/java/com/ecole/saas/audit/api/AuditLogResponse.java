package com.ecole.saas.audit.api;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditActorType;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditSeverity;
import com.ecole.saas.audit.domain.AuditLog;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
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
        Map<String, String> metadata,
        String correlationId,
        Instant occurredAt,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getCategory(),
                log.getAction(),
                log.getOutcome(),
                log.getSeverity(),
                log.getActorType(),
                log.getActorId(),
                log.getActorRoles(),
                log.getResourceType(),
                log.getResourceId(),
                log.getSummary(),
                log.parsedMetadata(),
                log.getCorrelationId(),
                log.getOccurredAt(),
                log.getCreatedAt());
    }
}
