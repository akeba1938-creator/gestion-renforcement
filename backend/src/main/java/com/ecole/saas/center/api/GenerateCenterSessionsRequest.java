package com.ecole.saas.center.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record GenerateCenterSessionsRequest(
        @NotNull(message = "La date de début est obligatoire.")
        LocalDate fromDate,
        @NotNull(message = "La date de fin est obligatoire.")
        LocalDate toDate) {
}
