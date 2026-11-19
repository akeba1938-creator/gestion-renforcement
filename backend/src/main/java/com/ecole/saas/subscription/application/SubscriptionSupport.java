package com.ecole.saas.subscription.application;

import com.ecole.saas.subscription.EffectiveAccessState;
import com.ecole.saas.subscription.SubscriptionSnapshot;
import com.ecole.saas.subscription.domain.Subscription;
import com.ecole.saas.subscription.domain.SubscriptionEvent;
import com.ecole.saas.subscription.domain.SubscriptionEventType;
import com.ecole.saas.subscription.domain.SubscriptionPlan;
import com.ecole.saas.subscription.domain.SubscriptionPlanNotFoundException;
import com.ecole.saas.subscription.domain.SubscriptionPolicy;
import com.ecole.saas.subscription.domain.SubscriptionStatus;
import com.ecole.saas.subscription.infrastructure.SubscriptionEventRepository;
import com.ecole.saas.subscription.infrastructure.SubscriptionPlanRepository;
import com.ecole.saas.subscription.infrastructure.SubscriptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;

final class SubscriptionSupport {

    private SubscriptionSupport() {
    }

    static Optional<Subscription> findCurrentSubscription(SubscriptionRepository subscriptionRepository) {
        return subscriptionRepository
                .findFirstByStatusInOrderByCreatedAtDesc(SubscriptionStatus.OPEN_STATUSES)
                .or(subscriptionRepository::findFirstByOrderByCreatedAtDesc);
    }

    static SubscriptionSnapshot toSnapshot(Subscription subscription, SubscriptionPlan plan, Instant now) {
        EffectiveAccessState accessState = SubscriptionPolicy.evaluateAccessState(subscription, now);
        return new SubscriptionSnapshot(
                subscription.getId(),
                subscription.getPlanId(),
                plan.getCode(),
                plan.getName(),
                subscription.getStatus().name(),
                accessState,
                accessState.isReadAllowed(),
                accessState.isWriteAllowed(),
                subscription.getBillingCycle().name(),
                subscription.getAmountXof(),
                subscription.effectiveQuotas(),
                subscription.getStartsAt(),
                subscription.getEndsAt(),
                subscription.getTrialEndsAt(),
                subscription.getGracePeriodEndsAt(),
                subscription.getSuspendedAt(),
                subscription.getCancelledAt(),
                subscription.getStatusReason());
    }

    static SubscriptionSnapshot toSnapshot(
            Subscription subscription,
            SubscriptionPlanRepository planRepository,
            Instant now) {
        SubscriptionPlan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new SubscriptionPlanNotFoundException("Forfait associé introuvable."));
        return toSnapshot(subscription, plan, now);
    }

    static Page<SubscriptionSnapshot> toSnapshotPage(
            Page<Subscription> page,
            SubscriptionPlanRepository planRepository,
            Instant now) {
        List<Subscription> items = page.getContent();
        if (items.isEmpty()) {
            return page.map(sub -> null);
        }
        Set<UUID> planIds = items.stream()
                .map(Subscription::getPlanId)
                .collect(Collectors.toSet());
        Map<UUID, SubscriptionPlan> plansById = planRepository.findAllById(planIds).stream()
                .collect(Collectors.toMap(SubscriptionPlan::getId, Function.identity()));

        return page.map(subscription -> {
            SubscriptionPlan plan = plansById.get(subscription.getPlanId());
            if (plan == null) {
                throw new SubscriptionPlanNotFoundException("Forfait associé introuvable.");
            }
            return toSnapshot(subscription, plan, now);
        });
    }

    static SubscriptionEvent recordEvent(
            SubscriptionEventRepository eventRepository,
            Subscription subscription,
            SubscriptionEventType eventType,
            SubscriptionStatus previousStatus,
            String reason,
            UUID actorId,
            Instant occurredAt) {
        int nextSequence = eventRepository.findMaxSequenceNoBySubscriptionId(subscription.getId()) + 1;
        SubscriptionEvent event = new SubscriptionEvent(
                subscription.getId(),
                nextSequence,
                eventType,
                previousStatus,
                subscription.getStatus(),
                subscription.getPlanId(),
                subscription.getAmountXof(),
                subscription.effectiveQuotas(),
                reason,
                actorId,
                occurredAt);
        return eventRepository.save(event);
    }
}
