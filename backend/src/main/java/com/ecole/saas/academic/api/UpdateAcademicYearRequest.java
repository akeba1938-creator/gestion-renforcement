package com.ecole.saas.academic.api;

import com.ecole.saas.academic.domain.PeriodSystem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateAcademicYearRequest(
        @NotBlank(message = "Le libellé de l'année scolaire est obligatoire.")
        @Size(max = 100, message = "Le libellé de l'année scolaire ne peut pas dépasser 100 caractères.")
        String label,
        @NotNull(message = "La date de début est obligatoire.")
        LocalDate startDate,
        @NotNull(message = "La date de fin est obligatoire.")
        LocalDate endDate,
        PeriodSystem periodSystem) {
}
