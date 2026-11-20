package com.ecole.saas.teacher.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AddTeacherSpecialtyRequest(
        @NotNull(message = "La matière est obligatoire.")
        UUID subjectId,
        Boolean primarySpecialty,
        @Size(max = 255)
        String notes) {
}
