package com.ecole.saas.subscription.application;

import com.ecole.saas.subscription.QuotaResource;
import com.ecole.saas.subscription.SubscriptionSnapshot;
import java.util.List;

public record TenantQuotasOverview(
        SubscriptionSnapshot subscription,
        List<QuotaUsageItem> resources) {

    public record QuotaUsageItem(
            QuotaResource resource,
            int baseLimit,
            int extraLimit,
            int effectiveLimit,
            Long currentUsage,
            Long remaining,
            Boolean withinLimit) {
    }
}
