package com.ecole.saas.finance.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateInvoiceRequest(
        @NotNull(message = "L'élève est obligatoire.") UUID studentId,
        @NotNull(message = "L'année scolaire est obligatoire.") UUID academicYearId,
        UUID payerGuardianId,
        UUID familyId,
        @NotBlank(message = "L'intitulé de la facture est obligatoire.")
                @Size(max = 150, message = "L'intitulé ne doit pas dépasser 150 caractères.")
                String title,
        @NotNull(message = "La date d'échéance est obligatoire.") LocalDate dueDate,
        @Size(max = 2000, message = "Les notes sont trop longues.") String notes,
        @NotEmpty(message = "Au moins une ligne de frais est obligatoire.") List<@Valid InvoiceLineRequest> lines,
        List<@Valid ScheduleItemRequest> schedules) {
}
