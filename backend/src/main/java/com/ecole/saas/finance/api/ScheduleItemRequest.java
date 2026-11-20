package com.ecole.saas.finance.api;

import com.ecole.saas.finance.application.StudentInvoiceService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ScheduleItemRequest(
        @NotBlank(message = "Le libellé d'échéance est obligatoire.")
                @Size(max = 120, message = "Le libellé ne doit pas dépasser 120 caractères.")
                String label,
        @NotNull(message = "La date d'échéance est obligatoire.") LocalDate dueDate,
        @Min(value = 0, message = "Le montant d'échéance doit être positif ou nul.") long amountXof) {

    public StudentInvoiceService.ScheduleItemInput toInput() {
        return new StudentInvoiceService.ScheduleItemInput(label, dueDate, amountXof);
    }
}
