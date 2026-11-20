package com.ecole.saas.school.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SchoolStatusReasonRequest(
        @NotBlank(message = "Le motif est obligatoire.")
        @Size(max = 255, message = "Le motif ne peut pas dépasser 255 caractères.")
        String reason) {
}
