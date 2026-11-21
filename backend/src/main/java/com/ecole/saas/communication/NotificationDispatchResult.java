package com.ecole.saas.communication;

import java.time.Instant;
import java.util.UUID;

/**
 * Résultat immuable d'un déclenchement de notification via {@link CommunicationPort}.
 */
public record NotificationDispatchResult(
        UUID campaignId,
        String reference,
        MessageCategory category,
        CommunicationChannel channel,
        CampaignStatus status,
        int totalRecipients,
        int sentOrDeliveredCount,
        int failedCount,
        long actualCostXof,
        boolean idempotentReplay,
        Instant dispatchedAt) {
}
