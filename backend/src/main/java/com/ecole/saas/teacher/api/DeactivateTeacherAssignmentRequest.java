package com.ecole.saas.teacher.api;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record DeactivateTeacherAssignmentRequest(
        LocalDate endDate,
        @Size(max = 500)
        String reason) {
}
