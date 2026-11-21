package com.ecole.saas.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Commande immuable d'enregistrement d'un événement dans le journal d'audit plateforme ({@code ADMIN_SAAS}).
 */
public record PlatformAuditRecordCommand(
        AuditCategory category,
        AuditAction action,
        AuditOutcome outcome,
        AuditSeverity severity,
        UUID targetSchoolId,
        String resourceType,
        UUID resourceId,
        String summary,
        String reason,
        UUID actorId,
        AuditActorType actorType,
        String actorRoles,
        Map<String, Object> metadata) {

    public PlatformAuditRecordCommand {
        Objects.requireNonNull(category, "La catégorie d'audit est obligatoire.");
        Objects.requireNonNull(action, "L'action d'audit est obligatoire.");
        outcome = outcome != null ? outcome : AuditOutcome.SUCCESS;
        severity = severity != null ? severity : AuditSeverity.INFO;
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("Le type de ressource d'audit est obligatoire.");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("Le résumé de l'événement d'audit est obligatoire.");
        }
        metadata = copyMetadata(metadata);
    }

    public static PlatformAuditRecordCommand success(
            AuditCategory category,
            AuditAction action,
            UUID targetSchoolId,
            String resourceType,
            UUID resourceId,
            String summary,
            String reason,
            Map<String, Object> metadata) {
        return new PlatformAuditRecordCommand(
                category,
                action,
                AuditOutcome.SUCCESS,
                AuditSeverity.INFO,
                targetSchoolId,
                resourceType,
                resourceId,
                summary,
                reason,
                null,
                null,
                null,
                metadata);
    }

    public static PlatformAuditRecordCommand critical(
            AuditCategory category,
            AuditAction action,
            AuditOutcome outcome,
            UUID targetSchoolId,
            String resourceType,
            UUID resourceId,
            String summary,
            String reason,
            Map<String, Object> metadata) {
        return new PlatformAuditRecordCommand(
                category,
                action,
                outcome,
                AuditSeverity.CRITICAL,
                targetSchoolId,
                resourceType,
                resourceId,
                summary,
                reason,
                null,
                null,
                null,
                metadata);
    }

    private static Map<String, Object> copyMetadata(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((k, v) -> {
            if (k != null && v != null) {
                copy.put(k, v);
            }
        });
        return Collections.unmodifiableMap(copy);
    }
}
