package com.ecole.saas.schooldocs.api;

import com.ecole.saas.schooldocs.DocumentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateDocumentTemplateRequest(
        @NotBlank(message = "Le nom du modèle est obligatoire.")
        @Size(max = 150, message = "Le nom du modèle ne doit pas dépasser 150 caractères.")
        String name,

        @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères.")
        String description,

        @NotNull(message = "Le type de document est obligatoire.")
        DocumentType documentType,

        @Size(max = 10, message = "Le code langue ne doit pas dépasser 10 caractères.")
        String language,

        @NotBlank(message = "Le modèle de titre est obligatoire.")
        @Size(max = 255, message = "Le modèle de titre ne doit pas dépasser 255 caractères.")
        String titleTemplate,

        @NotBlank(message = "Le corps du modèle est obligatoire.")
        @Size(max = 20000, message = "Le corps du modèle ne doit pas dépasser 20 000 caractères.")
        String bodyTemplate,

        @Size(max = 1000, message = "Le pied de page ne doit pas dépasser 1000 caractères.")
        String footerTemplate,

        @Min(value = 1, message = "La durée de validité doit être supérieure ou égale à 1 jour.")
        @Max(value = 3650, message = "La durée de validité ne peut pas dépasser 3650 jours.")
        Integer defaultValidityDays,

        @NotNull(message = "Le montant des frais en FCFA est obligatoire (0 si gratuit).")
        @Min(value = 0, message = "Le montant des frais en FCFA doit être positif ou nul.")
        Long feeAmountXof) {
}
