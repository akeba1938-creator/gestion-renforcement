package com.ecole.saas.center.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record EnrollCenterStudentRequest(
        @NotNull(message = "L'identifiant de l'élève est obligatoire.")
        UUID studentId,
        UUID studentPackId,
        LocalDate enrolledAt,
        @Size(max = 500, message = "Les notes ne doivent pas dépasser 500 caractères.")
        String notes) {
}
