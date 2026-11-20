package com.ecole.saas.center.api;

import com.ecole.saas.center.application.CenterSessionService;
import com.ecole.saas.center.domain.CenterSession;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CenterSessionResponse(
        UUID id,
        UUID groupId,
        UUID scheduleSlotId,
        UUID teacherId,
        UUID subjectId,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        int durationMinutes,
        String roomName,
        String topic,
        String status,
        UUID recordedBy,
        UUID completedBy,
        Instant completedAt,
        String cancelledReason,
        String notes,
        List<CenterSessionAttendanceResponse> attendances,
        Instant createdAt,
        Instant updatedAt) {

    static CenterSessionResponse from(CenterSessionService.SessionView view) {
        CenterSession session = view.session();
        List<CenterSessionAttendanceResponse> attendanceResponses = view.attendances().stream()
                .map(CenterSessionAttendanceResponse::from)
                .toList();
        return new CenterSessionResponse(
                session.getId(),
                session.getGroupId(),
                session.getScheduleSlotId(),
                session.getTeacherId(),
                session.getSubjectId(),
                session.getSessionDate(),
                session.getStartTime(),
                session.getEndTime(),
                session.getDurationMinutes(),
                session.getRoomName(),
                session.getTopic(),
                session.getStatus().name(),
                session.getRecordedBy(),
                session.getCompletedBy(),
                session.getCompletedAt(),
                session.getCancelledReason(),
                session.getNotes(),
                attendanceResponses,
                session.getCreatedAt(),
                session.getUpdatedAt());
    }
}
