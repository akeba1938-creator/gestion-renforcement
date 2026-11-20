package com.ecole.saas.student.api;

import com.ecole.saas.student.domain.Student;
import com.ecole.saas.student.domain.StudentGender;
import com.ecole.saas.student.domain.StudentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StudentResponse(
        UUID id,
        String matricule,
        String firstName,
        String lastName,
        StudentGender gender,
        LocalDate birthDate,
        String birthPlace,
        String nationality,
        String address,
        String phone,
        String email,
        String photoObjectKey,
        String medicalNotes,
        LocalDate admissionDate,
        StudentStatus status,
        String statusReason,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static StudentResponse from(Student student) {
        return new StudentResponse(
                student.getId(),
                student.getMatricule(),
                student.getFirstName(),
                student.getLastName(),
                student.getGender(),
                student.getBirthDate(),
                student.getBirthPlace(),
                student.getNationality(),
                student.getAddress(),
                student.getPhone(),
                student.getEmail(),
                student.getPhotoObjectKey(),
                student.getMedicalNotes(),
                student.getAdmissionDate(),
                student.getStatus(),
                student.getStatusReason(),
                student.getArchivedAt(),
                student.getCreatedAt(),
                student.getUpdatedAt());
    }
}
