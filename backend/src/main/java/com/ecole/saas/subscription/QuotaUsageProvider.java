package com.ecole.saas.subscription;

/**
 * Point d'extension (SPI) implémentable par les modules métier ({@code student}, {@code identity},
 * {@code schooldocs}, etc.) afin de remonter l'usage courant d'une ressource pour le tenant actif.
 */
public interface QuotaUsageProvider {

    QuotaResource resource();

    long currentUsageForCurrentTenant();
}
