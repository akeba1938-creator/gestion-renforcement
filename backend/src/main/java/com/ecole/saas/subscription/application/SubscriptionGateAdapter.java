package com.ecole.saas.subscription.application;

import com.ecole.saas.shared.application.SubscriptionGatePort;
import com.ecole.saas.subscription.SubscriptionAccessPort;
import org.springframework.stereotype.Component;

/**
 * Adaptateur exposant les gardes d'abonnement de {@link SubscriptionAccessPort} via
 * {@link SubscriptionGatePort} afin de préserver l'acyclicité Spring Modulith.
 */
@Component
class SubscriptionGateAdapter implements SubscriptionGatePort {

    private final SubscriptionAccessPort subscriptionAccessPort;

    SubscriptionGateAdapter(SubscriptionAccessPort subscriptionAccessPort) {
        this.subscriptionAccessPort = subscriptionAccessPort;
    }

    @Override
    public void assertReadAllowed() {
        subscriptionAccessPort.assertReadAllowed();
    }

    @Override
    public void requireOperationalSubscription() {
        subscriptionAccessPort.requireOperationalSubscription();
    }
}
