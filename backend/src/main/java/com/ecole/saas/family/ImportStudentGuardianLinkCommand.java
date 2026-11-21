package com.ecole.saas.family;

import java.util.UUID;

/**
 * Commande publique immuable d'import d'un lien élève-parent/tuteur légal.
 */
public record ImportStudentGuardianLinkCommand(
        UUID studentId,
        UUID guardianId,
        String relationshipType,
        Boolean primaryContact,
        Boolean financialResponsible,
        Boolean legalCustody,
        Boolean emergencyContact,
        Boolean authorizedPickup,
        String notes) {
}
