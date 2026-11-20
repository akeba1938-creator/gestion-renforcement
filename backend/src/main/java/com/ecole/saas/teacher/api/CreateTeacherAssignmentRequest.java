package com.ecole.saas.teacher.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTeacherAssignmentRequest(
        @NotNull(message = "L'enseignant est obligatoire.")
        UUID teacherId,
        @NotNull(message = "L'année scolaire est obligatoire.")
        UUID academicYearId,
        @NotNull(message = "La classe est obligatoire.")
        UUID classRoomId,
        @NotNull(message = "La matière est obligatoire.")
        UUID subjectId,
        Boolean homeroomTeacher,
        @Min(value = 1, message = "Le volume horaire hebdomadaire doit être compris entre 1 et 40.")
        @Max(value = 40, message = "Le volume horaire hebdomadaire doit être compris entre 1 et 40.")
        Integer weeklyHours,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 500)
        String notes) {
}
