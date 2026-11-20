package com.ecole.saas.center.api;

import com.ecole.saas.center.application.CenterSessionService;
import com.ecole.saas.center.domain.CenterSessionAttendance;
import java.time.Instant;
import java.util.UUID;

public record CenterSessionAttendanceResponse(
        UUID id,
        UUID sessionId,
        UUID studentId,
        String studentMatricule,
        String studentFirstName,
        String studentLastName,
        UUID enrollmentId,
        UUID consumedStudentPackId,
        String status,
        Integer lateMinutes,
        Integer participationRating,
        Boolean homeworkDone,
        String remarks,
        UUID recordedBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt) {

    static CenterSessionAttendanceResponse from(CenterSessionService.AttendanceView view) {
        CenterSessionAttendance attendance = view.attendance();
        return new CenterSessionAttendanceResponse(
                attendance.getId(),
                attendance.getSessionId(),
                attendance.getStudentId(),
                view.student() != null ? view.student().matricule() : null,
                view.student() != null ? view.student().firstName() : null,
                view.student() != null ? view.student().lastName() : null,
                attendance.getEnrollmentId(),
                attendance.getConsumedStudentPackId(),
                attendance.getStatus().name(),
                attendance.getLateMinutes(),
                attendance.getParticipationRating(),
                attendance.getHomeworkDone(),
                attendance.getRemarks(),
                attendance.getRecordedBy(),
                attendance.getUpdatedBy(),
                attendance.getCreatedAt(),
                attendance.getUpdatedAt());
    }
}
