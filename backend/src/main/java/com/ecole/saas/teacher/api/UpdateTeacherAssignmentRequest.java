package com.ecole.saas.teacher.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateTeacherAssignmentRequest(
        Boolean homeroomTeacher,
        @Min(value = 1, message = "Le volume horaire hebdomadaire doit être compris entre 1 et 40.")
        @Max(value = 40, message = "Le volume horaire hebdomadaire doit être compris entre 1 et 40.")
        Integer weeklyHours,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 500)
        String notes) {
}
