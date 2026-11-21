package com.ecole.saas.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Commande immuable d'enregistrement d'un événement dans le journal d'audit d'un établissement.
 * Les métadonnées sont systématiquement filtrées et assainies par le module {@code audit}.
 */
public record AuditRecordCommand(
        AuditCategory category,
        AuditAction action,
        AuditOutcome outcome,
        AuditSeverity severity,
        String resourceType,
        UUID resourceId,
        String summary,
        UUID actorId,
        AuditActorType actorType,
        String actorRoles,
        Map<String, Object> metadata) {

    public AuditRecordCommand {
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

    public static AuditRecordCommand success(
            AuditCategory category,
            AuditAction action,
            String resourceType,
            UUID resourceId,
            String summary,
            Map<String, Object> metadata) {
        return new AuditRecordCommand(
                category,
                action,
                AuditOutcome.SUCCESS,
                AuditSeverity.INFO,
                resourceType,
                resourceId,
                summary,
                null,
                null,
                null,
                metadata);
    }

    public static AuditRecordCommand warning(
            AuditCategory category,
            AuditAction action,
            AuditOutcome outcome,
            String resourceType,
            UUID resourceId,
            String summary,
            Map<String, Object> metadata) {
        return new AuditRecordCommand(
                category,
                action,
                outcome,
                AuditSeverity.WARNING,
                resourceType,
                resourceId,
                summary,
                null,
                null,
                null,
                metadata);
    }

    public static AuditRecordCommand critical(
            AuditCategory category,
            AuditAction action,
            AuditOutcome outcome,
            String resourceType,
            UUID resourceId,
            String summary,
            Map<String, Object> metadata) {
        return new AuditRecordCommand(
                category,
                action,
                outcome,
                AuditSeverity.CRITICAL,
                resourceType,
                resourceId,
                summary,
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
