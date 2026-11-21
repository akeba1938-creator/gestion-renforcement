package com.ecole.saas.subscription.application;

import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.subscription.EffectiveQuotas;
import com.ecole.saas.subscription.QuotaResource;
import com.ecole.saas.subscription.QuotaUsageProvider;
import com.ecole.saas.subscription.SubscriptionSnapshot;
import com.ecole.saas.subscription.application.TenantQuotasOverview.QuotaUsageItem;
import com.ecole.saas.subscription.domain.Subscription;
import com.ecole.saas.subscription.domain.SubscriptionEvent;
import com.ecole.saas.subscription.domain.SubscriptionNotFoundException;
import com.ecole.saas.subscription.infrastructure.SubscriptionEventRepository;
import com.ecole.saas.subscription.infrastructure.SubscriptionPlanRepository;
import com.ecole.saas.subscription.infrastructure.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de consultation de l'abonnement et des quotas pour les responsables d'établissement.
 * Le tenant est exclusivement issu de {@link TenantContext} (alimenté depuis le JWT côté serveur).
 */
@Service
@PreAuthorize("hasAnyRole('PROMOTER', 'DIRECTOR', 'CENTER_MANAGER')")
public class TenantSubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionEventRepository eventRepository;
    private final Map<QuotaResource, List<QuotaUsageProvider>> usageProviders;
    private final Clock clock;

    public TenantSubscriptionService(
            SubscriptionRepository subscriptionRepository,
            SubscriptionPlanRepository planRepository,
            SubscriptionEventRepository eventRepository,
            List<QuotaUsageProvider> providers,
            Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.eventRepository = eventRepository;
        this.usageProviders = new EnumMap<>(QuotaResource.class);
        if (providers != null) {
            for (QuotaUsageProvider provider : providers) {
                this.usageProviders
                        .computeIfAbsent(provider.resource(), ignored -> new ArrayList<>())
                        .add(provider);
            }
        }
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SubscriptionSnapshot getCurrentSubscription() {
        TenantContext.requireCurrent();
        Instant now = clock.instant();
        Subscription current = SubscriptionSupport.findCurrentSubscription(subscriptionRepository)
                .orElseThrow(() -> new SubscriptionNotFoundException(
                        "Aucun abonnement n'est associé à votre établissement."));
        return SubscriptionSupport.toSnapshot(current, planRepository, now);
    }

    @Transactional(readOnly = true)
    public TenantQuotasOverview getCurrentQuotasOverview() {
        SubscriptionSnapshot snapshot = getCurrentSubscription();
        EffectiveQuotas quotas = snapshot.quotas();
        List<QuotaUsageItem> items = Arrays.stream(QuotaResource.values())
                .map(resource -> buildUsageItem(resource, quotas))
                .toList();
        return new TenantQuotasOverview(snapshot, items);
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionSnapshot> listSubscriptions(Pageable pageable) {
        TenantContext.requireCurrent();
        Instant now = clock.instant();
        return SubscriptionSupport.toSnapshotPage(
                subscriptionRepository.findAll(pageable),
                planRepository,
                now);
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionEvent> listEvents(UUID subscriptionId, Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException("Abonnement introuvable."));
        return eventRepository.findBySubscriptionIdOrderBySequenceNoDesc(subscriptionId, pageable);
    }

    private QuotaUsageItem buildUsageItem(QuotaResource resource, EffectiveQuotas quotas) {
        int base = switch (resource) {
            case STUDENTS -> quotas.baseStudents();
            case USERS -> quotas.baseUsers();
            case STORAGE_MB -> quotas.baseStorageMb();
        };
        int extra = switch (resource) {
            case STUDENTS -> quotas.extraStudents();
            case USERS -> quotas.extraUsers();
            case STORAGE_MB -> quotas.extraStorageMb();
        };
        int effective = quotas.limitFor(resource);

        List<QuotaUsageProvider> providers = usageProviders.get(resource);
        if (providers == null || providers.isEmpty()) {
            return new QuotaUsageItem(resource, base, extra, effective, null, null, null);
        }
        long usage = 0L;
        for (QuotaUsageProvider provider : providers) {
            usage = Math.addExact(usage, Math.max(0L, provider.currentUsageForCurrentTenant()));
        }
        long remaining = Math.max(0L, (long) effective - usage);
        boolean withinLimit = usage <= effective;
        return new QuotaUsageItem(resource, base, extra, effective, usage, remaining, withinLimit);
    }
}
