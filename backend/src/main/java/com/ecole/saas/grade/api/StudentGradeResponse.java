package com.ecole.saas.grade.api;

import com.ecole.saas.grade.application.EvaluationService;
import com.ecole.saas.grade.domain.GradeAttendanceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StudentGradeResponse(
        UUID id,
        UUID evaluationId,
        UUID studentId,
        String studentMatricule,
        String studentFirstName,
        String studentLastName,
        GradeAttendanceStatus attendanceStatus,
        BigDecimal score,
        String comment,
        UUID enteredBy,
        Instant enteredAt,
        UUID updatedBy,
        Instant updatedAt) {

    public static StudentGradeResponse from(EvaluationService.StudentGradeView view) {
        return new StudentGradeResponse(
                view.grade().getId(),
                view.grade().getEvaluationId(),
                view.grade().getStudentId(),
                view.studentMatricule(),
                view.studentFirstName(),
                view.studentLastName(),
                view.grade().getAttendanceStatus(),
                view.grade().getScore(),
                view.grade().getComment(),
                view.grade().getEnteredBy(),
                view.grade().getEnteredAt(),
                view.grade().getUpdatedBy(),
                view.grade().getUpdatedAt());
    }
}
