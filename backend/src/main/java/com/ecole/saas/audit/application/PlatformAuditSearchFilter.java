package com.ecole.saas.audit.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditActorType;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditSeverity;
import java.time.Instant;
import java.util.UUID;

public record PlatformAuditSearchFilter(
        AuditCategory category,
        AuditAction action,
        AuditOutcome outcome,
        AuditSeverity severity,
        UUID actorId,
        AuditActorType actorType,
        UUID targetSchoolId,
        String resourceType,
        UUID resourceId,
        String correlationId,
        Instant from,
        Instant to,
        String search) {
}
