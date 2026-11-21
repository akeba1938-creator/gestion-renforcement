package com.ecole.saas.audit.api;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditActorType;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditSeverity;
import com.ecole.saas.audit.domain.PlatformAuditLog;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PlatformAuditLogResponse(
        UUID id,
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
        Map<String, String> metadata,
        String correlationId,
        Instant occurredAt,
        Instant createdAt) {

    public static PlatformAuditLogResponse from(PlatformAuditLog log) {
        return new PlatformAuditLogResponse(
                log.getId(),
                log.getCategory(),
                log.getAction(),
                log.getOutcome(),
                log.getSeverity(),
                log.getActorType(),
                log.getActorId(),
                log.getActorRoles(),
                log.getTargetSchoolId(),
                log.getResourceType(),
                log.getResourceId(),
                log.getSummary(),
                log.getReason(),
                log.parsedMetadata(),
                log.getCorrelationId(),
                log.getOccurredAt(),
                log.getCreatedAt());
    }
}
