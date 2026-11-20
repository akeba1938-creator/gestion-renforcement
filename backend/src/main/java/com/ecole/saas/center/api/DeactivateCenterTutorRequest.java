package com.ecole.saas.center.api;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record DeactivateCenterTutorRequest(
        LocalDate endDate,
        @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères.")
        String reason) {
}
