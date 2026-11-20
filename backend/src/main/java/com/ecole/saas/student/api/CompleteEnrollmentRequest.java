package com.ecole.saas.student.api;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CompleteEnrollmentRequest(
        LocalDate endDate,

        @Size(max = 255, message = "Le motif ne peut pas dépasser 255 caractères.")
        String reason) {
}
