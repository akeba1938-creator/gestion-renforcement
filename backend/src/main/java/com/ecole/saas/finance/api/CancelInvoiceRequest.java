package com.ecole.saas.finance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelInvoiceRequest(
        @NotBlank(message = "Le motif d'annulation est obligatoire.")
                @Size(max = 255, message = "Le motif ne doit pas dépasser 255 caractères.")
                String reason) {
}
