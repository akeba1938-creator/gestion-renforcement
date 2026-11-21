package com.ecole.saas.communication.api;

import com.ecole.saas.communication.application.CommunicationCampaignService;

public record RetryCampaignResponse(
        CommunicationCampaignResponse campaign,
        int retriedCount,
        int succeededCount) {

    public static RetryCampaignResponse from(CommunicationCampaignService.RetryResult result) {
        return new RetryCampaignResponse(
                CommunicationCampaignResponse.from(result.campaign()),
                result.retriedCount(),
                result.succeededCount());
    }
}
