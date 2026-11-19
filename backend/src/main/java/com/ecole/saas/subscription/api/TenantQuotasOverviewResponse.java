package com.ecole.saas.subscription.api;

import com.ecole.saas.subscription.EffectiveAccessState;
import com.ecole.saas.subscription.QuotaResource;
import com.ecole.saas.subscription.application.TenantQuotasOverview;
import com.ecole.saas.subscription.application.TenantQuotasOverview.QuotaUsageItem;
import java.util.List;
import java.util.UUID;

public record TenantQuotasOverviewResponse(
        UUID subscriptionId,
        String planCode,
        String planName,
        String status,
        EffectiveAccessState accessState,
        boolean readAllowed,
        boolean writeAllowed,
        List<QuotaItemResponse> resources) {

    public static TenantQuotasOverviewResponse from(TenantQuotasOverview overview) {
        return new TenantQuotasOverviewResponse(
                overview.subscription().subscriptionId(),
                overview.subscription().planCode(),
                overview.subscription().planName(),
                overview.subscription().status(),
                overview.subscription().accessState(),
                overview.subscription().readAllowed(),
                overview.subscription().writeAllowed(),
                overview.resources().stream().map(QuotaItemResponse::from).toList());
    }

    public record QuotaItemResponse(
            QuotaResource resource,
            int baseLimit,
            int extraLimit,
            int effectiveLimit,
            Long currentUsage,
            Long remaining,
            Boolean withinLimit) {

        public static QuotaItemResponse from(QuotaUsageItem item) {
            return new QuotaItemResponse(
                    item.resource(),
                    item.baseLimit(),
                    item.extraLimit(),
                    item.effectiveLimit(),
                    item.currentUsage(),
                    item.remaining(),
                    item.withinLimit());
        }
    }
}
