package com.ecole.saas.subscription.domain;

import com.ecole.saas.subscription.EffectiveAccessState;
import com.ecole.saas.subscription.QuotaCheckResult;
import com.ecole.saas.subscription.QuotaResource;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Règles métier pures d'évaluation d'état d'accès, de durées et de quotas d'abonnement.
 */
public final class SubscriptionPolicy {

    private SubscriptionPolicy() {
    }

    public static EffectiveAccessState evaluateAccessState(Subscription subscription, Instant now) {
        Objects.requireNonNull(now, "L'instant de référence est obligatoire.");
        if (subscription == null) {
            return EffectiveAccessState.MISSING;
        }
        return switch (subscription.getStatus()) {
            case TRIAL -> {
                Instant trialDeadline = subscription.getTrialEndsAt() != null
                        ? subscription.getTrialEndsAt()
                        : subscription.getEndsAt();
                boolean stillValid = now.isBefore(trialDeadline) && now.isBefore(subscription.getEndsAt());
                yield stillValid ? EffectiveAccessState.TRIAL_ACTIVE : EffectiveAccessState.EXPIRED;
            }
            case ACTIVE -> now.isBefore(subscription.getEndsAt())
                    ? EffectiveAccessState.ACTIVE
                    : EffectiveAccessState.EXPIRED;
            case PAST_DUE -> {
                Instant graceEndsAt = subscription.getGracePeriodEndsAt();
                yield (graceEndsAt != null && now.isBefore(graceEndsAt))
                        ? EffectiveAccessState.PAST_DUE_GRACE
                        : EffectiveAccessState.EXPIRED;
            }
            case SUSPENDED -> EffectiveAccessState.SUSPENDED;
            case CANCELLED -> EffectiveAccessState.CANCELLED;
        };
    }

    public static QuotaCheckResult evaluateQuota(
            Subscription subscription,
            QuotaResource resource,
            long currentUsage,
            long requestedAdditional,
            Instant now) {
        Objects.requireNonNull(resource, "La ressource de quota est obligatoire.");
        Objects.requireNonNull(now, "L'instant de référence est obligatoire.");
        if (currentUsage < 0) {
            throw new IllegalArgumentException("L'utilisation courante doit être positive ou nulle.");
        }
        if (requestedAdditional < 0) {
            throw new IllegalArgumentException("Le volume demandé doit être positif ou nul.");
        }

        EffectiveAccessState accessState = evaluateAccessState(subscription, now);
        if (subscription == null) {
            return new QuotaCheckResult(
                    resource,
                    currentUsage,
                    requestedAdditional,
                    0L,
                    0L,
                    false,
                    accessState,
                    accessDeniedMessage(accessState));
        }

        long limit = subscription.effectiveQuotas().limitFor(resource);
        long remaining = Math.max(0L, limit - currentUsage);

        if (!accessState.isWriteAllowed()) {
            return new QuotaCheckResult(
                    resource,
                    currentUsage,
                    requestedAdditional,
                    limit,
                    remaining,
                    false,
                    accessState,
                    accessDeniedMessage(accessState));
        }

        boolean fits = currentUsage <= limit && requestedAdditional <= (limit - currentUsage);
        if (!fits) {
            String message = "Quota " + resource.label() + " dépassé : utilisation actuelle "
                    + currentUsage + ", ajout demandé " + requestedAdditional
                    + ", plafond autorisé " + limit + ".";
            return new QuotaCheckResult(
                    resource,
                    currentUsage,
                    requestedAdditional,
                    limit,
                    remaining,
                    false,
                    accessState,
                    message);
        }

        return new QuotaCheckResult(
                resource,
                currentUsage,
                requestedAdditional,
                limit,
                remaining,
                true,
                accessState,
                null);
    }

    public static void assertReadAllowed(Subscription subscription, Instant now) {
        EffectiveAccessState accessState = evaluateAccessState(subscription, now);
        if (!accessState.isReadAllowed()) {
            throw new SubscriptionAccessDeniedException(accessDeniedMessage(accessState));
        }
    }

    public static void assertOperational(Subscription subscription, Instant now) {
        EffectiveAccessState accessState = evaluateAccessState(subscription, now);
        if (!accessState.isWriteAllowed()) {
            throw new SubscriptionAccessDeniedException(accessDeniedMessage(accessState));
        }
    }

    public static void assertQuotaAvailable(
            Subscription subscription,
            QuotaResource resource,
            long currentUsage,
            long requestedAdditional,
            Instant now) {
        QuotaCheckResult result = evaluateQuota(subscription, resource, currentUsage, requestedAdditional, now);
        if (!result.accessState().isWriteAllowed()) {
            throw new SubscriptionAccessDeniedException(result.reason());
        }
        if (!result.allowed()) {
            throw new SubscriptionQuotaExceededException(result.reason());
        }
    }

    public static Instant computeTrialEnd(Instant startsAt, int trialDays) {
        Objects.requireNonNull(startsAt, "La date de début est obligatoire.");
        if (trialDays <= 0 || trialDays > SubscriptionPlan.MAX_TRIAL_DAYS) {
            throw new IllegalArgumentException(
                    "La durée d'essai doit être comprise entre 1 et " + SubscriptionPlan.MAX_TRIAL_DAYS + " jours.");
        }
        return startsAt.plus(trialDays, ChronoUnit.DAYS);
    }

    public static Instant computePeriodEnd(Instant startsAt, BillingCycle billingCycle) {
        Objects.requireNonNull(startsAt, "La date de début est obligatoire.");
        Objects.requireNonNull(billingCycle, "Le cycle de facturation est obligatoire.");
        ZonedDateTime utcStart = startsAt.atZone(ZoneOffset.UTC);
        return billingCycle == BillingCycle.YEARLY
                ? utcStart.plusYears(1).toInstant()
                : utcStart.plusMonths(1).toInstant();
    }

    public static String accessDeniedMessage(EffectiveAccessState state) {
        return switch (state) {
            case MISSING -> "Aucun abonnement actif n'est associé à cet établissement.";
            case EXPIRED -> "L'abonnement ou la période d'essai a expiré. Le compte est en lecture seule.";
            case SUSPENDED -> "L'abonnement de cet établissement est suspendu.";
            case CANCELLED -> "L'abonnement de cet établissement a été annulé.";
            case TRIAL_ACTIVE, ACTIVE, PAST_DUE_GRACE -> "Abonnement opérationnel.";
        };
    }
}
