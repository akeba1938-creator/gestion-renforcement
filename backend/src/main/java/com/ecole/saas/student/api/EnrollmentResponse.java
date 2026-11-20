package com.ecole.saas.student.api;

import com.ecole.saas.student.application.StudentEnrollmentService.EnrollmentView;
import com.ecole.saas.student.domain.EnrollmentStatus;
import com.ecole.saas.student.domain.EnrollmentType;
import com.ecole.saas.student.domain.StudentEnrollment;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EnrollmentResponse(
        UUID id,
        UUID studentId,
        String studentMatricule,
        String studentFirstName,
        String studentLastName,
        UUID academicYearId,
        String academicYearCode,
        UUID classRoomId,
        String classRoomCode,
        String classRoomName,
        EnrollmentType enrollmentType,
        EnrollmentStatus status,
        LocalDate enrollmentDate,
        LocalDate endDate,
        String previousSchool,
        boolean repeating,
        String notes,
        String statusReason,
        Instant createdAt,
        Instant updatedAt) {

    public static EnrollmentResponse from(EnrollmentView view) {
        StudentEnrollment enrollment = view.enrollment();
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getStudentId(),
                view.studentMatricule(),
                view.studentFirstName(),
                view.studentLastName(),
                enrollment.getAcademicYearId(),
                view.academicYearCode(),
                enrollment.getClassRoomId(),
                view.classRoomCode(),
                view.classRoomName(),
                enrollment.getEnrollmentType(),
                enrollment.getStatus(),
                enrollment.getEnrollmentDate(),
                enrollment.getEndDate(),
                enrollment.getPreviousSchool(),
                enrollment.isRepeating(),
                enrollment.getNotes(),
                enrollment.getStatusReason(),
                enrollment.getCreatedAt(),
                enrollment.getUpdatedAt());
    }
}
