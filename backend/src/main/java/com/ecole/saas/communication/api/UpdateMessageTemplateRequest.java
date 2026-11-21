package com.ecole.saas.communication.api;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.MessageCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateMessageTemplateRequest(
        @NotBlank(message = "Le nom du modèle est obligatoire.")
        @Size(max = 120, message = "Le nom ne peut pas dépasser 120 caractères.")
        String name,
        @Size(max = 500, message = "La description ne peut pas dépasser 500 caractères.")
        String description,
        @NotNull(message = "La catégorie est obligatoire.")
        MessageCategory category,
        @NotNull(message = "Le canal est obligatoire.")
        CommunicationChannel channel,
        @Size(max = 10, message = "La langue ne peut pas dépasser 10 caractères.")
        String language,
        @Size(max = 255, message = "L'objet ne peut pas dépasser 255 caractères.")
        String subjectTemplate,
        @NotBlank(message = "Le corps du modèle est obligatoire.")
        @Size(max = 4000, message = "Le corps ne peut pas dépasser 4000 caractères.")
        String bodyTemplate,
        @Min(value = 0, message = "Le coût unitaire en FCFA doit être positif ou nul.")
        Long estimatedUnitCostXof) {
}
