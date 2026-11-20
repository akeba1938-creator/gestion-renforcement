package com.ecole.saas.finance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RequestPaymentReversalRequest(
        @NotNull(message = "L'identifiant d'encaissement est obligatoire.") UUID paymentId,
        @NotBlank(message = "Le motif de contre-passation est obligatoire.")
                @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères.")
                String reason) {
}
