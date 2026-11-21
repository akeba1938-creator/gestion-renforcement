package com.ecole.saas.communication.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelCampaignRequest(
        @NotBlank(message = "Le motif d'annulation est obligatoire.")
        @Size(max = 255, message = "Le motif ne peut pas dépasser 255 caractères.")
        String reason) {
}
