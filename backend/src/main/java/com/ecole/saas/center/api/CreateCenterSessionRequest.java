package com.ecole.saas.center.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateCenterSessionRequest(
        @NotNull(message = "La cohorte est obligatoire.")
        UUID groupId,
        UUID scheduleSlotId,
        UUID teacherId,
        UUID subjectId,
        @NotNull(message = "La date de séance est obligatoire.")
        LocalDate sessionDate,
        @NotNull(message = "L'heure de début est obligatoire.")
        LocalTime startTime,
        @NotNull(message = "L'heure de fin est obligatoire.")
        LocalTime endTime,
        @Size(max = 60, message = "Le nom de la salle ne doit pas dépasser 60 caractères.")
        String roomName,
        @Size(max = 200, message = "Le thème ne doit pas dépasser 200 caractères.")
        String topic,
        @Size(max = 500, message = "Les notes ne doivent pas dépasser 500 caractères.")
        String notes) {
}
