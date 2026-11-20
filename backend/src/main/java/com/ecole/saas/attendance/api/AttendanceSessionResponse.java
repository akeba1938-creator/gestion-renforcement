package com.ecole.saas.attendance.api;

import com.ecole.saas.attendance.application.AttendanceSessionService;
import com.ecole.saas.attendance.domain.AttendanceSessionStatus;
import com.ecole.saas.attendance.domain.AttendanceTimeSlot;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record AttendanceSessionResponse(
        UUID id,
        UUID academicYearId,
        UUID evaluationPeriodId,
        String periodCode,
        UUID classRoomId,
        String classRoomCode,
        String classRoomName,
        UUID subjectId,
        String subjectCode,
        String subjectName,
        UUID teacherId,
        String teacherFullName,
        LocalDate sessionDate,
        AttendanceTimeSlot timeSlot,
        LocalTime startTime,
        LocalTime endTime,
        String label,
        AttendanceSessionStatus status,
        UUID recordedBy,
        Instant submittedAt,
        UUID lockedBy,
        Instant lockedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        List<AttendanceRecordResponse> records) {

    public static AttendanceSessionResponse from(AttendanceSessionService.AttendanceSessionView view) {
        return new AttendanceSessionResponse(
                view.session().getId(),
                view.session().getAcademicYearId(),
                view.session().getEvaluationPeriodId(),
                view.periodCode(),
                view.session().getClassRoomId(),
                view.classRoomCode(),
                view.classRoomName(),
                view.session().getSubjectId(),
                view.subjectCode(),
                view.subjectName(),
                view.session().getTeacherId(),
                view.teacherFullName(),
                view.session().getSessionDate(),
                view.session().getTimeSlot(),
                view.session().getStartTime(),
                view.session().getEndTime(),
                view.session().getLabel(),
                view.session().getStatus(),
                view.session().getRecordedBy(),
                view.session().getSubmittedAt(),
                view.session().getLockedBy(),
                view.session().getLockedAt(),
                view.session().getNotes(),
                view.session().getCreatedAt(),
                view.session().getUpdatedAt(),
                view.records().stream().map(AttendanceRecordResponse::from).toList());
    }
}
