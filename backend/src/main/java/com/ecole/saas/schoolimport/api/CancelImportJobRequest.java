package com.ecole.saas.schoolimport.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requête d'annulation d'un lot d'import en attente de validation ou d'exécution.
 */
public record CancelImportJobRequest(
        @NotBlank(message = "Le motif d'annulation est obligatoire.")
        @Size(min = 5, max = 500, message = "Le motif d'annulation doit contenir entre 5 et 500 caractères.")
        String reason) {
}
