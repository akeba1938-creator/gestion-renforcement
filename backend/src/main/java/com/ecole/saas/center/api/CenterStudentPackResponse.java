package com.ecole.saas.center.api;

import com.ecole.saas.center.application.CenterPackService;
import com.ecole.saas.center.domain.CenterStudentPack;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CenterStudentPackResponse(
        UUID id,
        UUID studentId,
        String studentMatricule,
        String studentFirstName,
        String studentLastName,
        UUID packId,
        UUID groupId,
        String packCodeSnapshot,
        String packNameSnapshot,
        String billingMode,
        long priceXof,
        Integer totalSessions,
        int consumedSessions,
        Integer remainingSessions,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String statusReason,
        UUID assignedBy,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    static CenterStudentPackResponse from(CenterPackService.StudentPackView view) {
        CenterStudentPack pack = view.studentPack();
        return new CenterStudentPackResponse(
                pack.getId(),
                pack.getStudentId(),
                view.student() != null ? view.student().matricule() : null,
                view.student() != null ? view.student().firstName() : null,
                view.student() != null ? view.student().lastName() : null,
                pack.getPackId(),
                pack.getGroupId(),
                pack.getPackCodeSnapshot(),
                pack.getPackNameSnapshot(),
                pack.getBillingMode().name(),
                pack.getPriceXof(),
                pack.getTotalSessions(),
                pack.getConsumedSessions(),
                pack.getRemainingSessions(),
                pack.getStartDate(),
                pack.getEndDate(),
                pack.getStatus().name(),
                pack.getStatusReason(),
                pack.getAssignedBy(),
                pack.getNotes(),
                pack.getCreatedAt(),
                pack.getUpdatedAt());
    }

    static CenterStudentPackResponse fromEntity(CenterStudentPack pack) {
        return new CenterStudentPackResponse(
                pack.getId(),
                pack.getStudentId(),
                null,
                null,
                null,
                pack.getPackId(),
                pack.getGroupId(),
                pack.getPackCodeSnapshot(),
                pack.getPackNameSnapshot(),
                pack.getBillingMode().name(),
                pack.getPriceXof(),
                pack.getTotalSessions(),
                pack.getConsumedSessions(),
                pack.getRemainingSessions(),
                pack.getStartDate(),
                pack.getEndDate(),
                pack.getStatus().name(),
                pack.getStatusReason(),
                pack.getAssignedBy(),
                pack.getNotes(),
                pack.getCreatedAt(),
                pack.getUpdatedAt());
    }
}
