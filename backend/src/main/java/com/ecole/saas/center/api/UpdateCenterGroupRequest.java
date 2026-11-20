package com.ecole.saas.center.api;

import com.ecole.saas.center.domain.CenterGroupType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateCenterGroupRequest(
        UUID gradeLevelId,
        UUID subjectId,
        UUID defaultPackId,
        @NotBlank(message = "Le nom de la cohorte est obligatoire.")
        @Size(max = 120, message = "Le nom de la cohorte ne doit pas dépasser 120 caractères.")
        String name,
        @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères.")
        String description,
        @NotNull(message = "Le type de cohorte est obligatoire.")
        CenterGroupType groupType,
        @Size(max = 60, message = "Le nom de la salle ne doit pas dépasser 60 caractères.")
        String roomName,
        @NotNull(message = "La capacité est obligatoire.")
        @Min(value = 1, message = "La capacité minimale est de 1 élève.")
        @Max(value = 200, message = "La capacité maximale est de 200 élèves.")
        Integer capacity,
        @NotNull(message = "La date de début est obligatoire.")
        LocalDate startDate,
        LocalDate endDate) {
}
