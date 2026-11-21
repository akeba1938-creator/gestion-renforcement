package com.ecole.saas.communication.api;

import com.ecole.saas.communication.DeliveryStatus;
import com.ecole.saas.communication.MessageCategory;
import com.ecole.saas.communication.domain.MessageDelivery;
import java.time.Instant;
import java.util.UUID;

public record InboxMessageResponse(
        UUID id,
        UUID campaignId,
        MessageCategory category,
        UUID relatedStudentId,
        String subject,
        String body,
        DeliveryStatus status,
        boolean read,
        Instant deliveredAt,
        Instant readAt,
        Instant createdAt) {

    public static InboxMessageResponse from(MessageDelivery d) {
        return new InboxMessageResponse(
                d.getId(),
                d.getCampaignId(),
                d.getCategory(),
                d.getRelatedStudentId(),
                d.getRenderedSubject(),
                d.getRenderedBody(),
                d.getStatus(),
                d.getStatus() == DeliveryStatus.READ,
                d.getDeliveredAt(),
                d.getReadAt(),
                d.getCreatedAt());
    }
}
