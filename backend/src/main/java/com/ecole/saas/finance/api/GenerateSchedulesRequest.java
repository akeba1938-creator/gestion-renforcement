package com.ecole.saas.finance.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record GenerateSchedulesRequest(
        @Min(value = 1, message = "Le nombre d'échéances doit être compris entre 1 et 36.")
                @Max(value = 36, message = "Le nombre d'échéances doit être compris entre 1 et 36.")
                int installmentCount,
        @NotNull(message = "La date de première échéance est obligatoire.") LocalDate firstDueDate,
        @Min(value = 1, message = "L'intervalle en mois doit être compris entre 1 et 12.")
                @Max(value = 12, message = "L'intervalle en mois doit être compris entre 1 et 12.")
                int intervalMonths,
        @Size(max = 60, message = "Le préfixe de libellé ne doit pas dépasser 60 caractères.") String labelPrefix) {
}
