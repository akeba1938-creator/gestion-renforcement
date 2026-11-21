package com.ecole.saas.schoolimport.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Requête d'exécution contrôlée d'un lot d'import préalablement analysé.
 */
public record ExecuteImportJobRequest(
        @NotNull(message = "La confirmation explicite d'exécution est obligatoire.")
        Boolean confirmExecution,
        @Size(max = 128, message = "La clé d'idempotence ne doit pas dépasser 128 caractères.")
        String idempotencyKey) {
}
