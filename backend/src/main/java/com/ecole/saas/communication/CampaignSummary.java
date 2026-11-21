package com.ecole.saas.communication;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue immuable d'une campagne ou notification exposée aux autres modules applicatifs.
 */
public record CampaignSummary(
        UUID id,
        String reference,
        MessageCategory category,
        CommunicationChannel channel,
        AudienceType audienceType,
        UUID audienceTargetId,
        CampaignStatus status,
        int totalRecipients,
        int queuedCount,
        int sentCount,
        int deliveredCount,
        int failedCount,
        int readCount,
        long estimatedCostXof,
        long actualCostXof,
        Instant createdAt,
        Instant dispatchedAt) {
}
