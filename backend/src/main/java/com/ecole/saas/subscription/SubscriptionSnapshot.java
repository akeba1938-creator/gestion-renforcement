package com.ecole.saas.subscription;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue immuable de l'abonnement courant d'un établissement, exposée aux autres modules.
 */
public record SubscriptionSnapshot(
        UUID subscriptionId,
        UUID planId,
        String planCode,
        String planName,
        String status,
        EffectiveAccessState accessState,
        boolean readAllowed,
        boolean writeAllowed,
        String billingCycle,
        long amountXof,
        EffectiveQuotas quotas,
        Instant startsAt,
        Instant endsAt,
        Instant trialEndsAt,
        Instant gracePeriodEndsAt,
        Instant suspendedAt,
        Instant cancelledAt,
        String statusReason) {
}
