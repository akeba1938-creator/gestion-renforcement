package com.ecole.saas.center.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record UpsertCenterScheduleSlotRequest(
        UUID teacherId,
        UUID subjectId,
        @NotNull(message = "Le jour de la semaine est obligatoire.")
        DayOfWeek dayOfWeek,
        @NotNull(message = "L'heure de début est obligatoire.")
        LocalTime startTime,
        @NotNull(message = "L'heure de fin est obligatoire.")
        LocalTime endTime,
        @Size(max = 60, message = "Le nom de la salle ne doit pas dépasser 60 caractères.")
        String roomName,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil) {
}
