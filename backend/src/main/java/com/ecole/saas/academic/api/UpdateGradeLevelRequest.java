package com.ecole.saas.academic.api;

import com.ecole.saas.academic.domain.EducationCycle;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateGradeLevelRequest(
        @NotBlank(message = "Le nom du niveau est obligatoire.")
        @Size(max = 100, message = "Le nom du niveau ne peut pas dépasser 100 caractères.")
        String name,
        @NotNull(message = "Le cycle pédagogique est obligatoire.")
        EducationCycle cycle,
        @NotNull(message = "L'ordre d'affichage est obligatoire.")
        @Min(value = 1, message = "L'ordre d'affichage doit être strictement positif.")
        Integer displayOrder) {
}
