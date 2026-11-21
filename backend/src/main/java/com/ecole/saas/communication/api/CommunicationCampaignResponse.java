package com.ecole.saas.communication.api;

import com.ecole.saas.communication.AudienceType;
import com.ecole.saas.communication.CampaignStatus;
import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.GuardianTargetingMode;
import com.ecole.saas.communication.MessageCategory;
import com.ecole.saas.communication.domain.CampaignOrigin;
import com.ecole.saas.communication.domain.CommunicationCampaign;
import java.time.Instant;
import java.util.UUID;

public record CommunicationCampaignResponse(
        UUID id,
        String reference,
        CampaignOrigin origin,
        MessageCategory category,
        CommunicationChannel channel,
        UUID templateId,
        String title,
        AudienceType audienceType,
        UUID audienceTargetId,
        GuardianTargetingMode guardianTargetingMode,
        String subject,
        String body,
        boolean respectMarketingOptOut,
        Instant scheduledAt,
        CampaignStatus status,
        int totalRecipients,
        int queuedCount,
        int sentCount,
        int deliveredCount,
        int failedCount,
        int readCount,
        long unitCostXof,
        long estimatedCostXof,
        long actualCostXof,
        String sourceModule,
        String sourceResourceType,
        UUID sourceResourceId,
        UUID createdBy,
        Instant dispatchedAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancelledReason,
        Instant createdAt) {

    public static CommunicationCampaignResponse from(CommunicationCampaign c) {
        return new CommunicationCampaignResponse(
                c.getId(),
                c.getReference(),
                c.getOrigin(),
                c.getCategory(),
                c.getChannel(),
                c.getTemplateId(),
                c.getTitle(),
                c.getAudienceType(),
                c.getAudienceTargetId(),
                c.getGuardianTargetingMode(),
                c.getSubject(),
                c.getBody(),
                c.isRespectMarketingOptOut(),
                c.getScheduledAt(),
                c.getStatus(),
                c.getTotalRecipients(),
                c.getQueuedCount(),
                c.getSentCount(),
                c.getDeliveredCount(),
                c.getFailedCount(),
                c.getReadCount(),
                c.getUnitCostXof(),
                c.getEstimatedCostXof(),
                c.getActualCostXof(),
                c.getSourceModule(),
                c.getSourceResourceType(),
                c.getSourceResourceId(),
                c.getCreatedBy(),
                c.getDispatchedAt(),
                c.getCompletedAt(),
                c.getCancelledAt(),
                c.getCancelledReason(),
                c.getCreatedAt());
    }
}
