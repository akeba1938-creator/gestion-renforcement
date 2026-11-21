package com.ecole.saas.communication.application;

import com.ecole.saas.communication.domain.CommunicationConflictException;
import com.ecole.saas.communication.domain.CommunicationNotFoundException;
import com.ecole.saas.communication.domain.CommunicationSubscriptionReadDeniedException;
import com.ecole.saas.school.SchoolDirectory;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.subscription.SubscriptionAccessPort;
import com.ecole.saas.subscription.SubscriptionSnapshot;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class CommunicationSubscriptionGuard {

    private final SubscriptionAccessPort subscriptionAccessPort;
    private final SchoolDirectory schoolDirectory;

    CommunicationSubscriptionGuard(
            SubscriptionAccessPort subscriptionAccessPort,
            SchoolDirectory schoolDirectory) {
        this.subscriptionAccessPort = subscriptionAccessPort;
        this.schoolDirectory = schoolDirectory;
    }

    void requireReadAccess() {
        SubscriptionSnapshot snapshot = subscriptionAccessPort.findCurrentForCurrentTenant()
                .orElseThrow(() -> new CommunicationSubscriptionReadDeniedException(
                        "Aucun abonnement actif n'est associé à cet établissement."));
        if (!snapshot.readAllowed()) {
            throw new CommunicationSubscriptionReadDeniedException(
                    "L'état actuel de l'abonnement (" + snapshot.accessState()
                            + ") ne permet pas l'accès au module communication.");
        }
    }

    void requireWriteAccess() {
        subscriptionAccessPort.requireOperationalSubscription();
        SchoolProfileSnapshot school = requireSchoolProfile();
        if (!school.active()) {
            throw new CommunicationConflictException(
                    "school-inactive",
                    "L'établissement est actuellement inactif : les envois de communication sont bloqués.");
        }
    }

    SchoolProfileSnapshot requireSchoolProfile() {
        UUID tenantId = TenantContext.requireCurrent().value();
        return schoolDirectory.findProfileById(tenantId)
                .orElseThrow(() -> new CommunicationNotFoundException("Établissement introuvable."));
    }
}
