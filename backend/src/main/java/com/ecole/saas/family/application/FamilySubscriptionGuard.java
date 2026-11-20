package com.ecole.saas.family.application;

import com.ecole.saas.family.domain.FamilySubscriptionReadDeniedException;
import com.ecole.saas.subscription.SubscriptionAccessPort;
import com.ecole.saas.subscription.SubscriptionSnapshot;
import org.springframework.stereotype.Component;

/**
 * Garde d'accès abonnement pour le module {@code family} via {@link SubscriptionAccessPort}.
 */
@Component
class FamilySubscriptionGuard {

    private final SubscriptionAccessPort subscriptionAccessPort;

    FamilySubscriptionGuard(SubscriptionAccessPort subscriptionAccessPort) {
        this.subscriptionAccessPort = subscriptionAccessPort;
    }

    void requireReadAccess() {
        SubscriptionSnapshot snapshot = subscriptionAccessPort.findCurrentForCurrentTenant()
                .orElseThrow(() -> new FamilySubscriptionReadDeniedException(
                        "Aucun abonnement actif n'est associé à cet établissement."));
        if (!snapshot.readAllowed()) {
            throw new FamilySubscriptionReadDeniedException(
                    "L'état actuel de l'abonnement (" + snapshot.accessState()
                            + ") ne permet pas l'accès aux données familles et tuteurs.");
        }
    }

    void requireWriteAccess() {
        subscriptionAccessPort.requireOperationalSubscription();
    }
}
