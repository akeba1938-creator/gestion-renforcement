package com.ecole.saas.finance.api;

import com.ecole.saas.finance.domain.FeeCategory;
import com.ecole.saas.finance.domain.FeeFrequency;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateFeeItemRequest(
        @NotNull(message = "L'année scolaire est obligatoire.") UUID academicYearId,
        UUID gradeLevelId,
        @NotBlank(message = "Le code de frais est obligatoire.")
                @Size(min = 2, max = 32, message = "Le code de frais doit contenir entre 2 et 32 caractères.")
                String code,
        @NotBlank(message = "Le libellé du frais est obligatoire.")
                @Size(max = 150, message = "Le libellé ne doit pas dépasser 150 caractères.")
                String label,
        @NotNull(message = "La catégorie du frais est obligatoire.") FeeCategory category,
        @NotNull(message = "La périodicité du frais est obligatoire.") FeeFrequency frequency,
        @Min(value = 0, message = "Le montant en FCFA doit être positif ou nul.") long amountXof,
        boolean mandatory,
        @Size(max = 2000, message = "La description est trop longue.") String description) {
}
