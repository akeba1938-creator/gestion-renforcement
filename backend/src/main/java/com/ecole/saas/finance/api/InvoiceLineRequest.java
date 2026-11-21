package com.ecole.saas.finance.api;

import com.ecole.saas.finance.application.StudentInvoiceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record InvoiceLineRequest(
        @NotNull(message = "Le frais est obligatoire.") UUID feeItemId,
        @NotNull(message = "La quantité est obligatoire.")
                @Min(value = 1, message = "La quantité doit être au moins égale à 1.")
                @Max(value = 1000, message = "La quantité ne doit pas dépasser 1000.")
                Integer quantity,
        @Min(value = 0, message = "Le montant unitaire doit être positif ou nul.") Long unitAmountXofOverride,
        @Size(max = 150, message = "Le libellé ne doit pas dépasser 150 caractères.") String labelOverride) {

    public StudentInvoiceService.InvoiceLineInput toInput() {
        return new StudentInvoiceService.InvoiceLineInput(feeItemId, quantity, unitAmountXofOverride, labelOverride);
    }
}
