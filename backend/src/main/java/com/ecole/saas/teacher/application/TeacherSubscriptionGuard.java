package com.ecole.saas.teacher.application;

import com.ecole.saas.subscription.SubscriptionAccessPort;
import com.ecole.saas.subscription.SubscriptionSnapshot;
import com.ecole.saas.teacher.domain.TeacherSubscriptionReadDeniedException;
import org.springframework.stereotype.Component;

/**
 * Garde d'accès abonnement pour le module {@code teacher} via {@link SubscriptionAccessPort}.
 */
@Component
class TeacherSubscriptionGuard {

    private final SubscriptionAccessPort subscriptionAccessPort;

    TeacherSubscriptionGuard(SubscriptionAccessPort subscriptionAccessPort) {
        this.subscriptionAccessPort = subscriptionAccessPort;
    }

    void requireReadAccess() {
        SubscriptionSnapshot snapshot = subscriptionAccessPort.findCurrentForCurrentTenant()
                .orElseThrow(() -> new TeacherSubscriptionReadDeniedException(
                        "Aucun abonnement actif n'est associé à cet établissement."));
        if (!snapshot.readAllowed()) {
            throw new TeacherSubscriptionReadDeniedException(
                    "L'état actuel de l'abonnement (" + snapshot.accessState()
                            + ") ne permet pas l'accès aux données enseignants.");
        }
    }

    void requireWriteAccess() {
        subscriptionAccessPort.requireOperationalSubscription();
    }
}
