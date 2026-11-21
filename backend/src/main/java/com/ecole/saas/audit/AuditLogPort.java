package com.ecole.saas.audit;

import java.util.Optional;
import java.util.UUID;

/**
 * Frontière publique Spring Modulith du module {@code audit}.
 * Permet aux modules métier d'enregistrer des événements d'audit immuables en ajout seul
 * sans couplage aux entités internes ni aux tables du module {@code audit}.
 */
public interface AuditLogPort {

    /**
     * Enregistre un événement d'audit immuable pour l'établissement actif dans {@code TenantContext}.
     */
    void recordTenantEvent(AuditRecordCommand command);

    /**
     * Enregistre un événement d'audit immuable dans le journal d'un établissement cible
     * (utilisé par les opérations plateforme {@code ADMIN_SAAS} impactant un établissement).
     */
    void recordTenantEventForSchool(UUID schoolId, AuditRecordCommand command);

    /**
     * Enregistre un événement d'audit immuable dans le journal plateforme ({@code ADMIN_SAAS}).
     * Exige l'absence de contexte établissement actif afin de préserver la séparation RLS.
     */
    void recordPlatformEvent(PlatformAuditRecordCommand command);

    /**
     * Recherche un événement d'audit par identifiant dans l'établissement courant.
     */
    Optional<AuditEventSummary> findTenantEventById(UUID eventId);
}
