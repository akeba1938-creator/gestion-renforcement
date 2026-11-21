package com.ecole.saas.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Vue immuable en lecture seule d'un événement du journal d'audit établissement.
 */
public record AuditEventSummary(
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
        Instant occurredAt) {
}
