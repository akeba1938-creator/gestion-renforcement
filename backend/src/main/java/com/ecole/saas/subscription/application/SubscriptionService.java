package com.ecole.saas.subscription.application;

import com.ecole.saas.subscription.domain.BillingCycle;
import com.ecole.saas.subscription.domain.Subscription;
import com.ecole.saas.subscription.domain.SubscriptionPlan;
import com.ecole.saas.subscription.domain.SubscriptionStatus;
import com.ecole.saas.subscription.infrastructure.SubscriptionPlanRepository;
import com.ecole.saas.subscription.infrastructure.SubscriptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(
            SubscriptionPlanRepository planRepository,
            SubscriptionRepository subscriptionRepository) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
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
                .orElseThrow(() -> new IllegalArgumentException("Forfait introuvable."));
        long amountXof = billingCycle == BillingCycle.YEARLY
                ? plan.getYearlyPriceXof()
                : plan.getMonthlyPriceXof();
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
        return subscriptionRepository.save(subscription);
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
