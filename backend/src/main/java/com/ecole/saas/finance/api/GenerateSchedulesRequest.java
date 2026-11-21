package com.ecole.saas.finance.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record GenerateSchedulesRequest(
        @NotNull(message = "Le nombre d'échéances est obligatoire.")
                @Min(value = 1, message = "Le nombre d'échéances doit être compris entre 1 et 36.")
                @Max(value = 36, message = "Le nombre d'échéances doit être compris entre 1 et 36.")
                Integer installmentCount,
        @NotNull(message = "La date de première échéance est obligatoire.") LocalDate firstDueDate,
        @NotNull(message = "L'intervalle en mois est obligatoire.")
                @Min(value = 1, message = "L'intervalle en mois doit être compris entre 1 et 12.")
                @Max(value = 12, message = "L'intervalle en mois doit être compris entre 1 et 12.")
                Integer intervalMonths,
        @Size(max = 60, message = "Le préfixe de libellé ne doit pas dépasser 60 caractères.") String labelPrefix) {
}
