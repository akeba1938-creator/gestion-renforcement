package com.ecole.saas.finance.application;

import com.ecole.saas.finance.domain.FinanceSubscriptionReadDeniedException;
import com.ecole.saas.subscription.SubscriptionAccessPort;
import com.ecole.saas.subscription.SubscriptionSnapshot;
import org.springframework.stereotype.Component;

/**
 * Garde d'accès abonnement pour le module {@code finance} via {@link SubscriptionAccessPort}.
 * En abonnement {@code EXPIRED}, la lecture seule reste autorisée et toute écriture est bloquée ;
 * en {@code SUSPENDED}, {@code CANCELLED} ou absent, lecture et écriture sont refusées.
 */
@Component
class FinanceSubscriptionGuard {

    private final SubscriptionAccessPort subscriptionAccessPort;

    FinanceSubscriptionGuard(SubscriptionAccessPort subscriptionAccessPort) {
        this.subscriptionAccessPort = subscriptionAccessPort;
    }

    void requireReadAccess() {
        SubscriptionSnapshot snapshot = subscriptionAccessPort.findCurrentForCurrentTenant()
                .orElseThrow(() -> new FinanceSubscriptionReadDeniedException(
                        "Aucun abonnement actif n'est associé à cet établissement."));
        if (!snapshot.readAllowed()) {
            throw new FinanceSubscriptionReadDeniedException(
                    "L'état actuel de l'abonnement (" + snapshot.accessState()
                            + ") ne permet pas l'accès aux données financières.");
        }
    }

    void requireWriteAccess() {
        subscriptionAccessPort.requireOperationalSubscription();
    }
}
