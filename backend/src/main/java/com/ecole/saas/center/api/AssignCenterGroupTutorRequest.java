package com.ecole.saas.center.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record AssignCenterGroupTutorRequest(
        @NotNull(message = "L'identifiant de l'intervenant est obligatoire.")
        UUID teacherId,
        UUID subjectId,
        Boolean leadTutor,
        @Min(value = 0, message = "Le taux horaire en FCFA doit être positif ou nul.")
        Long hourlyRateXof,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 500, message = "Les notes ne doivent pas dépasser 500 caractères.")
        String notes) {
}
