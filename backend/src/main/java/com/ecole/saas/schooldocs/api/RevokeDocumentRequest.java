package com.ecole.saas.schooldocs.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeDocumentRequest(
        @NotBlank(message = "Le motif de révocation est obligatoire.")
        @Size(min = 5, max = 500, message = "Le motif de révocation doit contenir entre 5 et 500 caractères.")
        String reason) {
}
