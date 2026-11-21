package com.ecole.saas.family.api;

import com.ecole.saas.family.domain.RelationshipType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateStudentGuardianRequest(
        @NotNull(message = "Le lien de parenté est obligatoire.")
        RelationshipType relationshipType,

        Boolean primaryContact,
        Boolean financialResponsible,
        Boolean legalCustody,
        Boolean emergencyContact,
        Boolean authorizedPickup,

        @Size(max = 500, message = "Les observations ne peuvent pas dépasser 500 caractères.")
        String notes) {
}
