package com.ecole.saas.communication.api;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.DeliveryStatus;
import com.ecole.saas.communication.MessageCategory;
import com.ecole.saas.communication.domain.DeliveryFailureCode;
import com.ecole.saas.communication.domain.MessageDelivery;
import com.ecole.saas.communication.domain.RecipientType;
import java.time.Instant;
import java.util.UUID;

public record MessageDeliveryResponse(
        UUID id,
        UUID campaignId,
        RecipientType recipientType,
        UUID recipientId,
        UUID recipientUserId,
        UUID relatedStudentId,
        String recipientDisplayName,
        MessageCategory category,
        CommunicationChannel channel,
        String destinationAddress,
        String renderedSubject,
        String renderedBody,
        DeliveryStatus status,
        String providerName,
        String providerMessageId,
        DeliveryFailureCode failureCode,
        String failureReason,
        long unitCostXof,
        long chargedCostXof,
        int attemptCount,
        Instant queuedAt,
        Instant sentAt,
        Instant deliveredAt,
        Instant failedAt,
        Instant readAt) {

    public static MessageDeliveryResponse from(MessageDelivery d) {
        return new MessageDeliveryResponse(
                d.getId(),
                d.getCampaignId(),
                d.getRecipientType(),
                d.getRecipientId(),
                d.getRecipientUserId(),
                d.getRelatedStudentId(),
                d.getRecipientDisplayName(),
                d.getCategory(),
                d.getChannel(),
                d.getDestinationAddress(),
                d.getRenderedSubject(),
                d.getRenderedBody(),
                d.getStatus(),
                d.getProviderName(),
                d.getProviderMessageId(),
                d.getFailureCode(),
                d.getFailureReason(),
                d.getUnitCostXof(),
                d.getChargedCostXof(),
                d.getAttemptCount(),
                d.getQueuedAt(),
                d.getSentAt(),
                d.getDeliveredAt(),
                d.getFailedAt(),
                d.getReadAt());
    }
}
