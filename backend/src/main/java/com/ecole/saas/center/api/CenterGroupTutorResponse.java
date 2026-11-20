package com.ecole.saas.center.api;

import com.ecole.saas.center.application.CenterGroupService;
import com.ecole.saas.center.domain.CenterGroupTutor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CenterGroupTutorResponse(
        UUID id,
        UUID groupId,
        UUID teacherId,
        String teacherEmployeeCode,
        String teacherFirstName,
        String teacherLastName,
        UUID subjectId,
        boolean leadTutor,
        Long hourlyRateXof,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    static CenterGroupTutorResponse from(CenterGroupService.GroupTutorView view) {
        CenterGroupTutor assignment = view.assignment();
        return new CenterGroupTutorResponse(
                assignment.getId(),
                assignment.getGroupId(),
                assignment.getTeacherId(),
                view.teacher() != null ? view.teacher().employeeCode() : null,
                view.teacher() != null ? view.teacher().firstName() : null,
                view.teacher() != null ? view.teacher().lastName() : null,
                assignment.getSubjectId(),
                assignment.isLeadTutor(),
                assignment.getHourlyRateXof(),
                assignment.getStartDate(),
                assignment.getEndDate(),
                assignment.isActive(),
                assignment.getNotes(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt());
    }
}
