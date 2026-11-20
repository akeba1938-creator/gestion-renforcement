package com.ecole.saas.family.api;

import com.ecole.saas.family.domain.RelationshipType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record LinkGuardianRequest(
        @NotNull(message = "L'identifiant du parent ou tuteur est obligatoire.")
        UUID guardianId,

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
