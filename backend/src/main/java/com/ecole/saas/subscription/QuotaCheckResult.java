package com.ecole.saas.subscription;

/**
 * Résultat d'une évaluation de quota d'abonnement pour une ressource donnée.
 */
public record QuotaCheckResult(
        QuotaResource resource,
        long currentUsage,
        long requestedAdditional,
        long limit,
        long remaining,
        boolean allowed,
        EffectiveAccessState accessState,
        String reason) {
}
