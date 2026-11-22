package com.ecole.saas.subscription.application;

import com.ecole.saas.subscription.domain.BillingCycle;
import com.ecole.saas.subscription.domain.Subscription;
import com.ecole.saas.subscription.domain.SubscriptionEventType;
import com.ecole.saas.subscription.domain.SubscriptionPlan;
import com.ecole.saas.subscription.domain.SubscriptionPlanInactiveException;
import com.ecole.saas.subscription.domain.SubscriptionPlanNotFoundException;
import com.ecole.saas.subscription.domain.SubscriptionStatus;
import com.ecole.saas.subscription.infrastructure.SubscriptionEventRepository;
import com.ecole.saas.subscription.infrastructure.SubscriptionPlanRepository;
import com.ecole.saas.subscription.infrastructure.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service applicatif interne de base du module {@code subscription} (création/lecture dans le
 * contexte tenant courant). Les cas d'usage REST sécurisés par rôle passent par
 * {@link SubscriptionPlanService}, {@link PlatformSubscriptionService} et {@link TenantSubscriptionService}.
 */
@Service
@PreAuthorize("hasRole('ADMIN_SAAS')")
public class SubscriptionService {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionEventRepository eventRepository;
    private final Clock clock;

    public SubscriptionService(
            SubscriptionPlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionEventRepository eventRepository,
            Clock clock) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Transactional
    public SubscriptionPlan createPlan(
            String code,
            String name,
            long monthlyPriceXof,
            long yearlyPriceXof,
            int maxStudents,
            int maxUsers,
            int maxStorageMb) {
        return planRepository.save(new SubscriptionPlan(
                code, name, monthlyPriceXof, yearlyPriceXof, maxStudents, maxUsers, maxStorageMb));
    }

    @Transactional
    public Subscription createSubscription(
            UUID planId,
            SubscriptionStatus status,
            BillingCycle billingCycle,
            Instant startsAt,
            Instant endsAt) {
        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new SubscriptionPlanNotFoundException("Forfait introuvable."));
        if (!plan.isActive()) {
            throw new SubscriptionPlanInactiveException("Impossible de souscrire à un forfait inactif.");
        }
        long amountXof = status == SubscriptionStatus.TRIAL ? 0L : plan.priceFor(billingCycle);
        Subscription subscription = new Subscription(
                plan.getId(),
                status,
                billingCycle,
                amountXof,
                plan.getMaxStudents(),
                plan.getMaxUsers(),
                plan.getMaxStorageMb(),
                startsAt,
                endsAt);
        Subscription saved = subscriptionRepository.save(subscription);
        SubscriptionEventType initialEvent = status == SubscriptionStatus.TRIAL
                ? SubscriptionEventType.TRIAL_STARTED
                : SubscriptionEventType.ACTIVATED;
        SubscriptionSupport.recordEvent(
                eventRepository,
                saved,
                initialEvent,
                null,
                null,
                null,
                clock.instant());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Subscription> findAllForCurrentTenant() {
        return subscriptionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> findById(UUID id) {
        return subscriptionRepository.findById(id);
    }
}
