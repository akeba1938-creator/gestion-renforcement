package com.ecole.saas.finance.api;

import jakarta.validation.constraints.Size;

public record ValidateCashClosingRequest(
        @Size(max = 500, message = "La note de validation ne doit pas dépasser 500 caractères.")
                String validationNote) {
}
