package com.ecole.saas.academic.api;

import com.ecole.saas.academic.domain.SubjectCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSubjectRequest(
        @NotBlank(message = "Le nom de la matière est obligatoire.")
        @Size(max = 120, message = "Le nom de la matière ne peut pas dépasser 120 caractères.")
        String name,
        @Size(max = 30, message = "L'abréviation de la matière ne peut pas dépasser 30 caractères.")
        String shortName,
        SubjectCategory category,
        @Min(value = 1, message = "Le coefficient par défaut doit être compris entre 1 et 50.")
        @Max(value = 50, message = "Le coefficient par défaut doit être compris entre 1 et 50.")
        int defaultCoefficient) {
}
