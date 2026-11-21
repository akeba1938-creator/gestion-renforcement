package com.ecole.saas.schooldocs.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReissueDocumentRequest(
        @NotBlank(message = "Le motif de réédition/duplicata est obligatoire.")
        @Size(min = 5, max = 500, message = "Le motif de réédition/duplicata doit contenir entre 5 et 500 caractères.")
        String reason,

        @Min(value = 1, message = "La durée de validité doit être supérieure ou égale à 1 jour.")
        @Max(value = 3650, message = "La durée de validité ne peut pas dépasser 3650 jours.")
        Integer validityDays,

        @Min(value = 0, message = "Les frais de duplicata en FCFA doivent être positifs ou nuls.")
        Long feeAmountXof,

        @Size(max = 128, message = "La clé d'idempotence ne doit pas dépasser 128 caractères.")
        String idempotencyKey) {
}
