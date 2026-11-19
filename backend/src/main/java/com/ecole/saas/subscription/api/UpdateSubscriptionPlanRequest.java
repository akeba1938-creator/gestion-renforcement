package com.ecole.saas.subscription.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSubscriptionPlanRequest(
        @NotBlank(message = "Le nom du forfait est obligatoire.")
        @Size(max = 100, message = "Le nom du forfait ne peut pas dépasser 100 caractères.")
        String name,
        @Size(max = 1000, message = "La description ne peut pas dépasser 1000 caractères.")
        String description,
        @Min(value = 0, message = "Le tarif mensuel en FCFA doit être positif ou nul.")
        long monthlyPriceXof,
        @Min(value = 0, message = "Le tarif annuel en FCFA doit être positif ou nul.")
        long yearlyPriceXof,
        @Min(value = 1, message = "Le quota d'élèves doit être strictement positif.")
        int maxStudents,
        @Min(value = 1, message = "Le quota d'utilisateurs doit être strictement positif.")
        int maxUsers,
        @Min(value = 1, message = "Le quota de stockage (Mo) doit être strictement positif.")
        int maxStorageMb,
        @Min(value = 0, message = "La durée d'essai doit être positive ou nulle.")
        @Max(value = 365, message = "La durée d'essai ne peut pas dépasser 365 jours.")
        Integer trialDays) {
}
