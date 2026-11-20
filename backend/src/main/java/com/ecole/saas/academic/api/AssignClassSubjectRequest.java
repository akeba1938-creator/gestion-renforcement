package com.ecole.saas.academic.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignClassSubjectRequest(
        @NotNull(message = "La matière est obligatoire.")
        UUID subjectId,
        @Min(value = 1, message = "Le coefficient doit être compris entre 1 et 50.")
        @Max(value = 50, message = "Le coefficient doit être compris entre 1 et 50.")
        Integer coefficient,
        @Min(value = 1, message = "Le volume horaire hebdomadaire doit être compris entre 1 et 40 heures.")
        @Max(value = 40, message = "Le volume horaire hebdomadaire doit être compris entre 1 et 40 heures.")
        Integer weeklyHours,
        Boolean mandatory) {
}
