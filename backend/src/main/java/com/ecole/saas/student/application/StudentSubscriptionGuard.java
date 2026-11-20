package com.ecole.saas.student.application;

import com.ecole.saas.student.domain.StudentSubscriptionReadDeniedException;
import com.ecole.saas.subscription.QuotaResource;
import com.ecole.saas.subscription.SubscriptionAccessPort;
import com.ecole.saas.subscription.SubscriptionSnapshot;
import org.springframework.stereotype.Component;

/**
 * Garde d'accès abonnement et quotas pour le module {@code student} via {@link SubscriptionAccessPort}.
 */
@Component
class StudentSubscriptionGuard {

    private final SubscriptionAccessPort subscriptionAccessPort;

    StudentSubscriptionGuard(SubscriptionAccessPort subscriptionAccessPort) {
        this.subscriptionAccessPort = subscriptionAccessPort;
    }

    void requireReadAccess() {
        SubscriptionSnapshot snapshot = subscriptionAccessPort.findCurrentForCurrentTenant()
                .orElseThrow(() -> new StudentSubscriptionReadDeniedException(
                        "Aucun abonnement actif n'est associé à cet établissement."));
        if (!snapshot.readAllowed()) {
            throw new StudentSubscriptionReadDeniedException(
                    "L'état actuel de l'abonnement (" + snapshot.accessState()
                            + ") ne permet pas l'accès aux données élèves.");
        }
    }

    void requireWriteAccess() {
        subscriptionAccessPort.requireOperationalSubscription();
    }

    void assertStudentQuotaAvailable(long currentActiveStudents) {
        subscriptionAccessPort.assertQuotaAvailable(QuotaResource.STUDENTS, currentActiveStudents, 1L);
    }
}
