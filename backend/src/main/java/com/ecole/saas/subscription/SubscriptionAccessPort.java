package com.ecole.saas.subscription;

import java.util.Optional;

/**
 * Frontière publique du module {@code subscription} exposée aux autres modules applicatifs
 * pour vérifier l'état de l'abonnement et les quotas du tenant courant.
 */
public interface SubscriptionAccessPort {

    Optional<SubscriptionSnapshot> findCurrentForCurrentTenant();

    SubscriptionSnapshot assertReadAllowed();

    SubscriptionSnapshot requireOperationalSubscription();

    boolean isOperationalForCurrentTenant();

    QuotaCheckResult checkQuota(QuotaResource resource, long currentUsage, long requestedAdditional);

    void assertQuotaAvailable(QuotaResource resource, long currentUsage, long requestedAdditional);
}
