package com.ecole.saas.school.api;

import com.ecole.saas.school.domain.GradingPeriodSystem;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateTenantSchoolRequest(
        @NotBlank(message = "Le nom de l'établissement est obligatoire.")
        @Size(max = 150, message = "Le nom de l'établissement ne peut pas dépasser 150 caractères.")
        String name,
        @Size(max = 60, message = "Le nom court ne peut pas dépasser 60 caractères.")
        String shortName,
        @Size(max = 20, message = "Le numéro de téléphone ne peut pas dépasser 20 caractères.")
        @Pattern(
                regexp = "^(\\+[1-9]\\d{6,14})?$",
                message = "Le numéro de téléphone doit respecter le format E.164 (ex. +221770000001).")
        String phone,
        @Email(message = "L'adresse e-mail est invalide.")
        @Size(max = 255, message = "L'adresse e-mail ne peut pas dépasser 255 caractères.")
        String email,
        @Size(max = 500, message = "L'adresse ne peut pas dépasser 500 caractères.")
        String address,
        @Size(max = 100, message = "La ville ne peut pas dépasser 100 caractères.")
        String city,
        @Size(max = 100, message = "La région ne peut pas dépasser 100 caractères.")
        String region,
        @Size(max = 2, message = "Le code pays doit contenir 2 lettres (ex. SN).")
        String countryCode,
        @Size(max = 200, message = "La devise de l'établissement ne peut pas dépasser 200 caractères.")
        String motto,
        @Size(max = 60, message = "Le numéro d'autorisation ne peut pas dépasser 60 caractères.")
        String ministryCode,
        @Size(max = 255, message = "Le site web ne peut pas dépasser 255 caractères.")
        String website,
        @Size(max = 50, message = "Le fuseau horaire ne peut pas dépasser 50 caractères.")
        String timezone,
        GradingPeriodSystem gradingPeriodSystem,
        Integer gradingScale) {
}
