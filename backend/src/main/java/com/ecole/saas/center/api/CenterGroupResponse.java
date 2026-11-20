package com.ecole.saas.center.api;

import com.ecole.saas.center.application.CenterGroupService;
import com.ecole.saas.center.domain.CenterGroup;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CenterGroupResponse(
        UUID id,
        UUID academicYearId,
        UUID gradeLevelId,
        UUID subjectId,
        UUID defaultPackId,
        String code,
        String name,
        String description,
        String groupType,
        String roomName,
        int capacity,
        long activeEnrollmentCount,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String statusReason,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt) {

    static CenterGroupResponse from(CenterGroupService.GroupView view) {
        CenterGroup group = view.group();
        return new CenterGroupResponse(
                group.getId(),
                group.getAcademicYearId(),
                group.getGradeLevelId(),
                group.getSubjectId(),
                group.getDefaultPackId(),
                group.getCode(),
                group.getName(),
                group.getDescription(),
                group.getGroupType().name(),
                group.getRoomName(),
                group.getCapacity(),
                view.activeEnrollmentCount(),
                group.getStartDate(),
                group.getEndDate(),
                group.getStatus().name(),
                group.getStatusReason(),
                group.getArchivedAt(),
                group.getCreatedAt(),
                group.getUpdatedAt());
    }
}
