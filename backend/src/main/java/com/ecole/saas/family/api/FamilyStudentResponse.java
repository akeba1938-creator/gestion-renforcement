package com.ecole.saas.family.api;

import com.ecole.saas.student.StudentSummary;
import java.time.LocalDate;
import java.util.UUID;

public record FamilyStudentResponse(
        UUID id,
        String matricule,
        String firstName,
        String lastName,
        String gender,
        LocalDate birthDate,
        String status,
        boolean active) {

    public static FamilyStudentResponse from(StudentSummary student) {
        return new FamilyStudentResponse(
                student.id(),
                student.matricule(),
                student.firstName(),
                student.lastName(),
                student.gender(),
                student.birthDate(),
                student.status(),
                student.active());
    }
}
