package com.ecole.saas.communication;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Commande générique immuable permettant à un module applicatif de déclencher
 * une notification idempotente via {@link CommunicationPort}.
 */
public record TriggerNotificationCommand(
        String idempotencyKey,
        MessageCategory category,
        CommunicationChannel channel,
        AudienceType audienceType,
        UUID audienceTargetId,
        GuardianTargetingMode guardianTargetingMode,
        String templateCode,
        String subject,
        String body,
        Map<String, String> variables,
        boolean respectMarketingOptOut,
        String sourceModule,
        String sourceResourceType,
        UUID sourceResourceId) {

    public TriggerNotificationCommand {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("La clé d'idempotence de notification est obligatoire.");
        }
        Objects.requireNonNull(category, "La catégorie de notification est obligatoire.");
        Objects.requireNonNull(channel, "Le canal de diffusion est obligatoire.");
        Objects.requireNonNull(audienceType, "Le type d'audience est obligatoire.");
        guardianTargetingMode = guardianTargetingMode != null
                ? guardianTargetingMode
                : GuardianTargetingMode.ALL_ACTIVE;
        variables = copyVariables(variables);
    }

    private static Map<String, String> copyVariables(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        source.forEach((k, v) -> {
            if (k != null && !k.isBlank() && v != null) {
                copy.put(k.trim(), v);
            }
        });
        return Collections.unmodifiableMap(copy);
    }
}
