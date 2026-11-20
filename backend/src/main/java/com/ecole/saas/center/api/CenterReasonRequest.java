package com.ecole.saas.center.api;

import jakarta.validation.constraints.Size;

public record CenterReasonRequest(
        @Size(max = 255, message = "Le motif ne doit pas dépasser 255 caractères.")
        String reason) {
}
