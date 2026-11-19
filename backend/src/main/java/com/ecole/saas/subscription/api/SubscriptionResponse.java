package com.ecole.saas.subscription.api;

import com.ecole.saas.subscription.EffectiveAccessState;
import com.ecole.saas.subscription.SubscriptionSnapshot;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID planId,
        String planCode,
        String planName,
        String status,
        EffectiveAccessState accessState,
        boolean readAllowed,
        boolean writeAllowed,
        String billingCycle,
        long amountXof,
        EffectiveQuotasResponse quotas,
        Instant startsAt,
        Instant endsAt,
        Instant trialEndsAt,
        Instant gracePeriodEndsAt,
        Instant suspendedAt,
        Instant cancelledAt,
        String statusReason) {

    public static SubscriptionResponse from(SubscriptionSnapshot snapshot) {
        return new SubscriptionResponse(
                snapshot.subscriptionId(),
                snapshot.planId(),
                snapshot.planCode(),
                snapshot.planName(),
                snapshot.status(),
                snapshot.accessState(),
                snapshot.readAllowed(),
                snapshot.writeAllowed(),
                snapshot.billingCycle(),
                snapshot.amountXof(),
                EffectiveQuotasResponse.from(snapshot.quotas()),
                snapshot.startsAt(),
                snapshot.endsAt(),
                snapshot.trialEndsAt(),
                snapshot.gracePeriodEndsAt(),
                snapshot.suspendedAt(),
                snapshot.cancelledAt(),
                snapshot.statusReason());
    }
}
