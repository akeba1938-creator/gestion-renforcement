package com.ecole.saas.student.api;

import com.ecole.saas.student.domain.StudentGender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateStudentRequest(
        @NotBlank(message = "Le prénom de l'élève est obligatoire.")
        @Size(max = 100, message = "Le prénom ne peut pas dépasser 100 caractères.")
        String firstName,

        @NotBlank(message = "Le nom de l'élève est obligatoire.")
        @Size(max = 100, message = "Le nom ne peut pas dépasser 100 caractères.")
        String lastName,

        @NotNull(message = "Le genre de l'élève est obligatoire.")
        StudentGender gender,

        @NotNull(message = "La date de naissance est obligatoire.")
        LocalDate birthDate,

        @Size(max = 120, message = "Le lieu de naissance ne peut pas dépasser 120 caractères.")
        String birthPlace,

        @Size(max = 80, message = "La nationalité ne peut pas dépasser 80 caractères.")
        String nationality,

        @Size(max = 500, message = "L'adresse ne peut pas dépasser 500 caractères.")
        String address,

        @Size(max = 20, message = "Le téléphone ne peut pas dépasser 20 caractères.")
        String phone,

        @Size(max = 255, message = "L'e-mail ne peut pas dépasser 255 caractères.")
        String email,

        @Size(max = 1000, message = "Les notes médicales ne peuvent pas dépasser 1000 caractères.")
        String medicalNotes,

        LocalDate admissionDate) {
}
