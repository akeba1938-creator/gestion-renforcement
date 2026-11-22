package com.ecole.saas.shared.application;

/**
 * Contrat minimal de contrôle d'abonnement exposé dans le module ouvert {@code shared}
 * pour permettre aux modules fondateurs ({@code school}, {@code audit}) de vérifier les droits
 * d'accès lecture/écriture sans introduire de cycle Spring Modulith avec {@code subscription}.
 */
public interface SubscriptionGatePort {

    void assertReadAllowed();

    void requireOperationalSubscription();
}
