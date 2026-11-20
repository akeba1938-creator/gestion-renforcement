package com.ecole.saas.center.application;

import com.ecole.saas.center.domain.CenterSubscriptionReadDeniedException;
import com.ecole.saas.subscription.SubscriptionAccessPort;
import com.ecole.saas.subscription.SubscriptionSnapshot;
import org.springframework.stereotype.Component;

@Component
class CenterSubscriptionGuard {

    private final SubscriptionAccessPort subscriptionAccessPort;

    CenterSubscriptionGuard(SubscriptionAccessPort subscriptionAccessPort) {
        this.subscriptionAccessPort = subscriptionAccessPort;
    }

    void requireReadAccess() {
        SubscriptionSnapshot snapshot = subscriptionAccessPort.findCurrentForCurrentTenant()
                .orElseThrow(() -> new CenterSubscriptionReadDeniedException(
                        "Aucun abonnement actif n'est associé à cet établissement."));
        if (!snapshot.readAllowed()) {
            throw new CenterSubscriptionReadDeniedException(
                    "L'état actuel de l'abonnement (" + snapshot.accessState()
                            + ") ne permet pas l'accès au module centre de renforcement.");
        }
    }

    void requireWriteAccess() {
        subscriptionAccessPort.requireOperationalSubscription();
    }
}
