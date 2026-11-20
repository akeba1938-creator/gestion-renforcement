package com.ecole.saas.student.api;

import com.ecole.saas.student.domain.EnrollmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record EnrollStudentRequest(
        @NotNull(message = "L'identifiant de l'élève est obligatoire.")
        UUID studentId,

        @NotNull(message = "L'identifiant de l'année scolaire est obligatoire.")
        UUID academicYearId,

        @NotNull(message = "L'identifiant de la classe est obligatoire.")
        UUID classRoomId,

        EnrollmentType enrollmentType,

        LocalDate enrollmentDate,

        @Size(max = 150, message = "L'établissement d'origine ne peut pas dépasser 150 caractères.")
        String previousSchool,

        Boolean repeating,

        @Size(max = 500, message = "Les observations ne peuvent pas dépasser 500 caractères.")
        String notes) {
}
