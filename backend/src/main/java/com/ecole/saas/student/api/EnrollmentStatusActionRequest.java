package com.ecole.saas.student.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record EnrollmentStatusActionRequest(
        LocalDate endDate,

        @NotBlank(message = "Le motif est obligatoire.")
        @Size(max = 255, message = "Le motif ne peut pas dépasser 255 caractères.")
        String reason) {
}
