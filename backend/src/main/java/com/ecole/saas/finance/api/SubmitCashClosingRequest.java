package com.ecole.saas.finance.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SubmitCashClosingRequest(
        @NotNull(message = "La date de clôture est obligatoire.") LocalDate closingDate,
        @NotNull(message = "Le montant compté en caisse est obligatoire.")
                @Min(value = 0, message = "Le montant compté en caisse doit être positif ou nul.")
                Long countedCashXof,
        @Size(max = 500, message = "Les notes ne doivent pas dépasser 500 caractères.") String notes) {
}
