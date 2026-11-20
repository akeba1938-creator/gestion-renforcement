package com.ecole.saas.teacher.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeacherStatusActionRequest(
        @NotBlank(message = "Le motif est obligatoire.")
        @Size(max = 255)
        String reason) {
}
