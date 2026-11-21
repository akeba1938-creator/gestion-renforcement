package com.ecole.saas.subscription.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSubscriptionPlanRequest(
        @NotBlank(message = "Le code du forfait est obligatoire.")
        @Size(max = 40, message = "Le code du forfait ne peut pas dépasser 40 caractères.")
        String code,
        @NotBlank(message = "Le nom du forfait est obligatoire.")
        @Size(max = 100, message = "Le nom du forfait ne peut pas dépasser 100 caractères.")
        String name,
        @Size(max = 1000, message = "La description ne peut pas dépasser 1000 caractères.")
        String description,
        @NotNull(message = "Le tarif mensuel en FCFA est obligatoire.")
        @Min(value = 0, message = "Le tarif mensuel en FCFA doit être positif ou nul.")
        Long monthlyPriceXof,
        @NotNull(message = "Le tarif annuel en FCFA est obligatoire.")
        @Min(value = 0, message = "Le tarif annuel en FCFA doit être positif ou nul.")
        Long yearlyPriceXof,
        @NotNull(message = "Le quota d'élèves est obligatoire.")
        @Min(value = 1, message = "Le quota d'élèves doit être strictement positif.")
        Integer maxStudents,
        @NotNull(message = "Le quota d'utilisateurs est obligatoire.")
        @Min(value = 1, message = "Le quota d'utilisateurs doit être strictement positif.")
        Integer maxUsers,
        @NotNull(message = "Le quota de stockage (Mo) est obligatoire.")
        @Min(value = 1, message = "Le quota de stockage (Mo) doit être strictement positif.")
        Integer maxStorageMb,
        @Min(value = 0, message = "La durée d'essai doit être positive ou nulle.")
        @Max(value = 365, message = "La durée d'essai ne peut pas dépasser 365 jours.")
        Integer trialDays) {
}
