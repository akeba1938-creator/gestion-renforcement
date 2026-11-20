package com.ecole.saas.teacher.api;

import com.ecole.saas.teacher.domain.TeacherGender;
import com.ecole.saas.teacher.domain.TeacherType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateTeacherRequest(
        @NotBlank(message = "Le prénom est obligatoire.")
        @Size(max = 100)
        String firstName,
        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 100)
        String lastName,
        TeacherGender gender,
        @NotNull(message = "Le type d'enseignant est obligatoire.")
        TeacherType teacherType,
        @NotBlank(message = "Le téléphone principal est obligatoire.")
        @Size(max = 20)
        String phone,
        @Size(max = 20)
        String secondaryPhone,
        @Size(max = 255)
        String email,
        @Size(max = 500)
        String address,
        @Size(max = 60)
        String nationalIdNumber,
        LocalDate hireDate,
        @Min(value = 0, message = "Le taux horaire en FCFA doit être positif ou nul.")
        Long hourlyRateXof,
        @Size(max = 1000)
        String notes) {
}
