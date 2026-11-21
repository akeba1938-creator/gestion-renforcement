package com.ecole.saas.subscription.application;

import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.subscription.QuotaCheckResult;
import com.ecole.saas.subscription.QuotaResource;
import com.ecole.saas.subscription.SubscriptionAccessPort;
import com.ecole.saas.subscription.SubscriptionSnapshot;
import com.ecole.saas.subscription.domain.Subscription;
import com.ecole.saas.subscription.domain.SubscriptionPolicy;
import com.ecole.saas.subscription.infrastructure.SubscriptionPlanRepository;
import com.ecole.saas.subscription.infrastructure.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation de la frontière publique {@link SubscriptionAccessPort} consommée par les autres
 * modules applicatifs pour contrôler l'état d'abonnement et les quotas du tenant courant.
 */
@Service
public class SubscriptionGuardService implements SubscriptionAccessPort {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final Clock clock;

    public SubscriptionGuardService(
            SubscriptionRepository subscriptionRepository,
            SubscriptionPlanRepository planRepository,
            Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SubscriptionSnapshot> findCurrentForCurrentTenant() {
        TenantContext.requireCurrent();
        Instant now = clock.instant();
        return SubscriptionSupport.findCurrentSubscription(subscriptionRepository)
                .map(subscription -> SubscriptionSupport.toSnapshot(subscription, planRepository, now));
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionSnapshot assertReadAllowed() {
        TenantContext.requireCurrent();
        Instant now = clock.instant();
        Subscription subscription = SubscriptionSupport.findCurrentSubscription(subscriptionRepository)
                .orElse(null);
        SubscriptionPolicy.assertReadAllowed(subscription, now);
        return SubscriptionSupport.toSnapshot(subscription, planRepository, now);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionSnapshot requireOperationalSubscription() {
        TenantContext.requireCurrent();
        Instant now = clock.instant();
        Subscription subscription = SubscriptionSupport.findCurrentSubscription(subscriptionRepository)
                .orElse(null);
        SubscriptionPolicy.assertOperational(subscription, now);
        return SubscriptionSupport.toSnapshot(subscription, planRepository, now);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOperationalForCurrentTenant() {
        TenantContext.requireCurrent();
        Instant now = clock.instant();
        Subscription subscription = SubscriptionSupport.findCurrentSubscription(subscriptionRepository)
                .orElse(null);
        return SubscriptionPolicy.evaluateAccessState(subscription, now).isWriteAllowed();
    }

    @Override
    @Transactional(readOnly = true)
    public QuotaCheckResult checkQuota(QuotaResource resource, long currentUsage, long requestedAdditional) {
        TenantContext.requireCurrent();
        Instant now = clock.instant();
        Subscription subscription = SubscriptionSupport.findCurrentSubscription(subscriptionRepository)
                .orElse(null);
        return SubscriptionPolicy.evaluateQuota(subscription, resource, currentUsage, requestedAdditional, now);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertQuotaAvailable(QuotaResource resource, long currentUsage, long requestedAdditional) {
        TenantContext.requireCurrent();
        Instant now = clock.instant();
        Subscription subscription = SubscriptionSupport.findCurrentSubscription(subscriptionRepository)
                .orElse(null);
        SubscriptionPolicy.assertQuotaAvailable(subscription, resource, currentUsage, requestedAdditional, now);
    }
}
