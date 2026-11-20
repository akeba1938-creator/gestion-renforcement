package com.ecole.saas.family.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFamilyRequest(
        @Size(max = 32, message = "Le code famille ne peut pas dépasser 32 caractères.")
        String code,

        @NotBlank(message = "Le nom de la famille est obligatoire.")
        @Size(max = 150, message = "Le nom de la famille ne peut pas dépasser 150 caractères.")
        String name,

        @Size(max = 20, message = "Le téléphone ne peut pas dépasser 20 caractères.")
        String primaryPhone,

        @Size(max = 255, message = "L'e-mail ne peut pas dépasser 255 caractères.")
        String email,

        @Size(max = 500, message = "L'adresse ne peut pas dépasser 500 caractères.")
        String address,

        @Size(max = 100, message = "La ville ne peut pas dépasser 100 caractères.")
        String city,

        @Size(max = 1000, message = "Les observations ne peuvent pas dépasser 1000 caractères.")
        String notes) {
}
