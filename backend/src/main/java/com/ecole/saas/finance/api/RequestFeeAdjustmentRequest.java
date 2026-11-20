package com.ecole.saas.finance.api;

import com.ecole.saas.finance.domain.AdjustmentCalculationMode;
import com.ecole.saas.finance.domain.AdjustmentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestFeeAdjustmentRequest(
        @NotNull(message = "Le type d'ajustement est obligatoire.") AdjustmentType adjustmentType,
        @NotNull(message = "Le mode de calcul est obligatoire.") AdjustmentCalculationMode calculationMode,
        @Min(value = 1, message = "Le montant fixe en FCFA doit être strictement positif.") Long fixedAmountXof,
        @Min(value = 1, message = "Le pourcentage doit être compris entre 1 et 100.")
                @Max(value = 100, message = "Le pourcentage doit être compris entre 1 et 100.")
                Integer percentageValue,
        @NotBlank(message = "Le motif est obligatoire.")
                @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères.")
                String reason) {
}
