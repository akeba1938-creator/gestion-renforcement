package com.ecole.saas.academic.api;

import com.ecole.saas.academic.domain.PeriodSystem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateAcademicYearRequest(
        @NotBlank(message = "Le code de l'année scolaire est obligatoire.")
        @Size(min = 2, max = 20, message = "Le code de l'année scolaire doit contenir entre 2 et 20 caractères.")
        String code,
        @NotBlank(message = "Le libellé de l'année scolaire est obligatoire.")
        @Size(max = 100, message = "Le libellé de l'année scolaire ne peut pas dépasser 100 caractères.")
        String label,
        @NotNull(message = "La date de début est obligatoire.")
        LocalDate startDate,
        @NotNull(message = "La date de fin est obligatoire.")
        LocalDate endDate,
        PeriodSystem periodSystem) {
}
