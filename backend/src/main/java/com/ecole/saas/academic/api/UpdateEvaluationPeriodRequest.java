package com.ecole.saas.academic.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateEvaluationPeriodRequest(
        @NotBlank(message = "Le nom de la période est obligatoire.")
        @Size(max = 100, message = "Le nom de la période ne peut pas dépasser 100 caractères.")
        String name,
        @Min(value = 1, message = "L'ordre de la période doit être strictement positif.")
        int sequenceOrder,
        @NotNull(message = "La date de début de période est obligatoire.")
        LocalDate startDate,
        @NotNull(message = "La date de fin de période est obligatoire.")
        LocalDate endDate,
        @Min(value = 1, message = "Le poids doit être compris entre 1 et 100.")
        @Max(value = 100, message = "Le poids doit être compris entre 1 et 100.")
        int weight) {
}
