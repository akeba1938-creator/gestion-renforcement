package com.ecole.saas.teacher.api;

import com.ecole.saas.teacher.domain.Teacher;
import com.ecole.saas.teacher.domain.TeacherGender;
import com.ecole.saas.teacher.domain.TeacherStatus;
import com.ecole.saas.teacher.domain.TeacherType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TeacherResponse(
        UUID id,
        String employeeCode,
        UUID userId,
        String firstName,
        String lastName,
        TeacherGender gender,
        TeacherType teacherType,
        String phone,
        String secondaryPhone,
        String email,
        String address,
        String nationalIdNumber,
        LocalDate hireDate,
        Long hourlyRateXof,
        TeacherStatus status,
        String statusReason,
        Instant archivedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static TeacherResponse from(Teacher teacher) {
        return new TeacherResponse(
                teacher.getId(),
                teacher.getEmployeeCode(),
                teacher.getUserId(),
                teacher.getFirstName(),
                teacher.getLastName(),
                teacher.getGender(),
                teacher.getTeacherType(),
                teacher.getPhone(),
                teacher.getSecondaryPhone(),
                teacher.getEmail(),
                teacher.getAddress(),
                teacher.getNationalIdNumber(),
                teacher.getHireDate(),
                teacher.getHourlyRateXof(),
                teacher.getStatus(),
                teacher.getStatusReason(),
                teacher.getArchivedAt(),
                teacher.getNotes(),
                teacher.getCreatedAt(),
                teacher.getUpdatedAt());
    }
}
