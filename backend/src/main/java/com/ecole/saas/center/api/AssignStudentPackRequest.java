package com.ecole.saas.center.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record AssignStudentPackRequest(
        @NotNull(message = "L'identifiant élève est obligatoire.")
        UUID studentId,
        @NotNull(message = "L'identifiant du forfait est obligatoire.")
        UUID packId,
        UUID groupId,
        @Min(value = 0, message = "Le montant FCFA doit être positif ou nul.")
        Long customPriceXof,
        @Min(value = 1, message = "Le nombre de séances doit être au moins 1.")
        @Max(value = 500, message = "Le nombre de séances ne doit pas dépasser 500.")
        Integer customTotalSessions,
        @NotNull(message = "La date de début est obligatoire.")
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 500, message = "Les notes ne doivent pas dépasser 500 caractères.")
        String notes) {
}
