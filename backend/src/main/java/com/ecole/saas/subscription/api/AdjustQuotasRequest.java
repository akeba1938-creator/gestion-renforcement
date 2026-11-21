package com.ecole.saas.subscription.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdjustQuotasRequest(
        @NotNull(message = "L'extension de quota élèves est obligatoire.")
        @Min(value = 0, message = "L'extension de quota élèves doit être positive ou nulle.")
        Integer extraStudents,
        @NotNull(message = "L'extension de quota utilisateurs est obligatoire.")
        @Min(value = 0, message = "L'extension de quota utilisateurs doit être positive ou nulle.")
        Integer extraUsers,
        @NotNull(message = "L'extension de quota stockage (Mo) est obligatoire.")
        @Min(value = 0, message = "L'extension de quota stockage (Mo) doit être positive ou nulle.")
        Integer extraStorageMb,
        @NotBlank(message = "Le motif d'ajustement des quotas est obligatoire.")
        @Size(max = 255, message = "Le motif ne peut pas dépasser 255 caractères.")
        String reason) {
}
