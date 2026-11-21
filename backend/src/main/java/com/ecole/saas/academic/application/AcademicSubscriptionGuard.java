package com.ecole.saas.academic.application;

import com.ecole.saas.subscription.SubscriptionAccessPort;
import org.springframework.stereotype.Component;

/**
 * Garde d'abonnement pour le module {@code academic} :
 * <ul>
 *   <li>Lecture autorisée en {@code TRIAL_ACTIVE}, {@code ACTIVE}, {@code PAST_DUE_GRACE} et {@code EXPIRED}.</li>
 *   <li>Écriture autorisée uniquement sur un abonnement opérationnel ({@code requireOperationalSubscription}).</li>
 *   <li>Lecture et écriture bloquées (403) en {@code SUSPENDED}, {@code CANCELLED} ou {@code MISSING}.</li>
 * </ul>
 */
@Component
class AcademicSubscriptionGuard {

    private final SubscriptionAccessPort subscriptionAccessPort;

    AcademicSubscriptionGuard(SubscriptionAccessPort subscriptionAccessPort) {
        this.subscriptionAccessPort = subscriptionAccessPort;
    }

    void requireReadAccess() {
        subscriptionAccessPort.assertReadAllowed();
    }

    void requireWriteAccess() {
        subscriptionAccessPort.requireOperationalSubscription();
    }
}
