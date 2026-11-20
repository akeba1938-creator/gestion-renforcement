package com.ecole.saas.attendance.api;

import com.ecole.saas.attendance.application.AttendanceSessionService;
import com.ecole.saas.attendance.domain.AttendanceRecordStatus;
import java.time.Instant;
import java.util.UUID;

public record AttendanceRecordResponse(
        UUID id,
        UUID sessionId,
        UUID studentId,
        String studentMatricule,
        String studentFirstName,
        String studentLastName,
        AttendanceRecordStatus status,
        Integer lateMinutes,
        boolean justified,
        String justificationReason,
        String justificationDocumentRef,
        UUID justifiedBy,
        Instant justifiedAt,
        String notes,
        UUID recordedBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt) {

    public static AttendanceRecordResponse from(AttendanceSessionService.AttendanceRecordView view) {
        return new AttendanceRecordResponse(
                view.record().getId(),
                view.record().getSessionId(),
                view.record().getStudentId(),
                view.studentMatricule(),
                view.studentFirstName(),
                view.studentLastName(),
                view.record().getStatus(),
                view.record().getLateMinutes(),
                view.record().isJustified(),
                view.record().getJustificationReason(),
                view.record().getJustificationDocumentRef(),
                view.record().getJustifiedBy(),
                view.record().getJustifiedAt(),
                view.record().getNotes(),
                view.record().getRecordedBy(),
                view.record().getUpdatedBy(),
                view.record().getCreatedAt(),
                view.record().getUpdatedAt());
    }
}
