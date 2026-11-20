package com.ecole.saas.family.api;

import com.ecole.saas.family.domain.GuardianGender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateGuardianRequest(
        UUID familyId,

        @NotBlank(message = "Le prénom du parent ou tuteur est obligatoire.")
        @Size(max = 100, message = "Le prénom ne peut pas dépasser 100 caractères.")
        String firstName,

        @NotBlank(message = "Le nom du parent ou tuteur est obligatoire.")
        @Size(max = 100, message = "Le nom ne peut pas dépasser 100 caractères.")
        String lastName,

        GuardianGender gender,

        @NotBlank(message = "Le numéro de téléphone principal est obligatoire.")
        @Size(max = 20, message = "Le téléphone ne peut pas dépasser 20 caractères.")
        String phone,

        @Size(max = 20, message = "Le téléphone secondaire ne peut pas dépasser 20 caractères.")
        String secondaryPhone,

        @Size(max = 255, message = "L'e-mail ne peut pas dépasser 255 caractères.")
        String email,

        @Size(max = 120, message = "La profession ne peut pas dépasser 120 caractères.")
        String profession,

        @Size(max = 500, message = "L'adresse ne peut pas dépasser 500 caractères.")
        String address,

        @Size(max = 60, message = "Le numéro de pièce d'identité ne peut pas dépasser 60 caractères.")
        String nationalIdNumber,

        @Size(max = 10, message = "La langue préférée ne peut pas dépasser 10 caractères.")
        String preferredLanguage) {
}
