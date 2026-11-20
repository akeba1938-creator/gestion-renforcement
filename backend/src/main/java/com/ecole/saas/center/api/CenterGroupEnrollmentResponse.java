package com.ecole.saas.center.api;

import com.ecole.saas.center.application.CenterGroupService;
import com.ecole.saas.center.domain.CenterGroupEnrollment;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CenterGroupEnrollmentResponse(
        UUID id,
        UUID groupId,
        UUID studentId,
        String studentMatricule,
        String studentFirstName,
        String studentLastName,
        UUID studentPackId,
        LocalDate enrolledAt,
        LocalDate endedAt,
        String status,
        String statusReason,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    static CenterGroupEnrollmentResponse from(CenterGroupService.GroupEnrollmentView view) {
        CenterGroupEnrollment enrollment = view.enrollment();
        return new CenterGroupEnrollmentResponse(
                enrollment.getId(),
                enrollment.getGroupId(),
                enrollment.getStudentId(),
                view.student() != null ? view.student().matricule() : null,
                view.student() != null ? view.student().firstName() : null,
                view.student() != null ? view.student().lastName() : null,
                enrollment.getStudentPackId(),
                enrollment.getEnrolledAt(),
                enrollment.getEndedAt(),
                enrollment.getStatus().name(),
                enrollment.getStatusReason(),
                enrollment.getNotes(),
                enrollment.getCreatedAt(),
                enrollment.getUpdatedAt());
    }
}
