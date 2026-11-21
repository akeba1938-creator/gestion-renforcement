package com.ecole.saas.schooldocs.api;

import jakarta.validation.constraints.Size;
import java.util.Map;

public record PreviewDocumentTemplateRequest(
        @Size(max = 50, message = "Le nombre maximal de variables d'aperçu est de 50.")
        Map<String, String> variables) {
}
