package com.ecole.saas.communication.api;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.DeliveryStatus;
import com.ecole.saas.communication.application.CommunicationCampaignService;
import java.util.Map;

public record CommunicationSummaryResponse(
        long totalCampaigns,
        long totalDeliveries,
        Map<DeliveryStatus, Long> deliveriesByStatus,
        Map<CommunicationChannel, Long> deliveriesByChannel,
        long totalEstimatedCostXof,
        long totalActualCostXof) {

    public static CommunicationSummaryResponse from(CommunicationCampaignService.CommunicationSummaryView view) {
        return new CommunicationSummaryResponse(
                view.totalCampaigns(),
                view.totalDeliveries(),
                view.deliveriesByStatus(),
                view.deliveriesByChannel(),
                view.totalEstimatedCostXof(),
                view.totalActualCostXof());
    }
}
