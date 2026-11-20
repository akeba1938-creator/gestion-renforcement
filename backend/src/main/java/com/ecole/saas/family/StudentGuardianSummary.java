package com.ecole.saas.family;

import java.util.UUID;

public record StudentGuardianSummary(
        UUID linkId,
        UUID studentId,
        UUID guardianId,
        UUID familyId,
        String guardianFirstName,
        String guardianLastName,
        String guardianPhone,
        String guardianEmail,
        String relationshipType,
        boolean primaryContact,
        boolean financialResponsible,
        boolean legalCustody,
        boolean emergencyContact,
        boolean authorizedPickup,
        boolean active) {
}
