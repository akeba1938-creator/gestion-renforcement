package com.ecole.saas.grade.api;

import com.ecole.saas.grade.application.EvaluationService;
import com.ecole.saas.grade.domain.EvaluationStatus;
import com.ecole.saas.grade.domain.EvaluationType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EvaluationResponse(
        UUID id,
        UUID academicYearId,
        UUID evaluationPeriodId,
        String periodCode,
        String periodStatus,
        UUID classRoomId,
        String classRoomCode,
        String classRoomName,
        UUID subjectId,
        UUID classSubjectId,
        String subjectCode,
        String subjectName,
        UUID teacherId,
        String teacherFullName,
        String title,
        EvaluationType evaluationType,
        LocalDate evaluationDate,
        BigDecimal maxScore,
        int weight,
        EvaluationStatus status,
        UUID validatedBy,
        Instant validatedAt,
        UUID lockedBy,
        Instant lockedAt,
        UUID createdBy,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static EvaluationResponse from(EvaluationService.EvaluationView view) {
        return new EvaluationResponse(
                view.evaluation().getId(),
                view.evaluation().getAcademicYearId(),
                view.evaluation().getEvaluationPeriodId(),
                view.periodCode(),
                view.periodStatus(),
                view.evaluation().getClassRoomId(),
                view.classRoomCode(),
                view.classRoomName(),
                view.evaluation().getSubjectId(),
                view.evaluation().getClassSubjectId(),
                view.subjectCode(),
                view.subjectName(),
                view.evaluation().getTeacherId(),
                view.teacherFullName(),
                view.evaluation().getTitle(),
                view.evaluation().getEvaluationType(),
                view.evaluation().getEvaluationDate(),
                view.evaluation().getMaxScore(),
                view.evaluation().getWeight(),
                view.evaluation().getStatus(),
                view.evaluation().getValidatedBy(),
                view.evaluation().getValidatedAt(),
                view.evaluation().getLockedBy(),
                view.evaluation().getLockedAt(),
                view.evaluation().getCreatedBy(),
                view.evaluation().getNotes(),
                view.evaluation().getCreatedAt(),
                view.evaluation().getUpdatedAt());
    }
}
