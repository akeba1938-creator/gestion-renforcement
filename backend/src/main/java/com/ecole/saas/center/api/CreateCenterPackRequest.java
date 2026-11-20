package com.ecole.saas.center.api;

import com.ecole.saas.center.domain.CenterPackBillingMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCenterPackRequest(
        UUID academicYearId,
        UUID gradeLevelId,
        UUID subjectId,
        @NotBlank(message = "Le code du forfait est obligatoire.")
        @Size(min = 2, max = 30, message = "Le code du forfait doit contenir entre 2 et 30 caractères.")
        String code,
        @NotBlank(message = "Le libellé du forfait est obligatoire.")
        @Size(max = 120, message = "Le libellé du forfait ne doit pas dépasser 120 caractères.")
        String name,
        @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères.")
        String description,
        @NotNull(message = "Le mode de facturation est obligatoire.")
        CenterPackBillingMode billingMode,
        @Min(value = 1, message = "Le nombre de séances doit être au moins 1.")
        @Max(value = 500, message = "Le nombre de séances ne doit pas dépasser 500.")
        Integer includedSessions,
        @Min(value = 1, message = "La durée de validité doit être d'au moins 1 jour.")
        @Max(value = 365, message = "La durée de validité ne doit pas dépasser 365 jours.")
        Integer durationDays,
        @NotNull(message = "Le prix en FCFA est obligatoire.")
        @Min(value = 0, message = "Le prix en FCFA doit être positif ou nul.")
        Long priceXof) {
}
