package com.ecole.saas.subscription.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdjustQuotasRequest(
        @Min(value = 0, message = "L'extension de quota élèves doit être positive ou nulle.")
        int extraStudents,
        @Min(value = 0, message = "L'extension de quota utilisateurs doit être positive ou nulle.")
        int extraUsers,
        @Min(value = 0, message = "L'extension de quota stockage (Mo) doit être positive ou nulle.")
        int extraStorageMb,
        @NotBlank(message = "Le motif d'ajustement des quotas est obligatoire.")
        @Size(max = 255, message = "Le motif ne peut pas dépasser 255 caractères.")
        String reason) {
}
