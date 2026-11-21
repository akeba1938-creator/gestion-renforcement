package com.ecole.saas.schooldocs.api;

import com.ecole.saas.schooldocs.DocumentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record IssueDocumentRequest(
        UUID templateId,

        DocumentType documentType,

        @NotNull(message = "L'identifiant de l'élève est obligatoire.")
        UUID studentId,

        UUID guardianId,

        UUID academicYearId,

        UUID classRoomId,

        @Size(max = 255, message = "Le titre ne doit pas dépasser 255 caractères.")
        String title,

        @Size(max = 20000, message = "Le corps du document ne doit pas dépasser 20 000 caractères.")
        String body,

        @Size(max = 1000, message = "Le pied de page ne doit pas dépasser 1000 caractères.")
        String footer,

        @Size(max = 50, message = "Le nombre maximal de variables personnalisées est de 50.")
        Map<String, String> variables,

        @Min(value = 1, message = "La durée de validité doit être supérieure ou égale à 1 jour.")
        @Max(value = 3650, message = "La durée de validité ne peut pas dépasser 3650 jours.")
        Integer validityDays,

        @Min(value = 0, message = "Les frais en FCFA doivent être positifs ou nuls.")
        Long feeAmountXof,

        @Size(max = 128, message = "La clé d'idempotence ne doit pas dépasser 128 caractères.")
        String idempotencyKey) {
}
