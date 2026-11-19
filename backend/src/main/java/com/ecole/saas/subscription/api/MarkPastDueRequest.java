package com.ecole.saas.subscription.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record MarkPastDueRequest(
        Instant gracePeriodEndsAt,
        @NotBlank(message = "Le motif de passage en impayé est obligatoire.")
        @Size(max = 255, message = "Le motif ne peut pas dépasser 255 caractères.")
        String reason) {
}
