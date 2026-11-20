package com.ecole.saas.grade.api;

import com.ecole.saas.grade.domain.PeriodSubjectResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PeriodSubjectResultResponse(
        UUID id,
        UUID subjectId,
        UUID classSubjectId,
        String subjectCode,
        String subjectName,
        int coefficient,
        BigDecimal averageScore,
        BigDecimal classSubjectAverage,
        Integer rankInSubject,
        int evaluationCount,
        String teacherAppreciation,
        UUID appreciatedBy,
        Instant appreciatedAt) {

    public static PeriodSubjectResultResponse from(PeriodSubjectResult result) {
        return new PeriodSubjectResultResponse(
                result.getId(),
                result.getSubjectId(),
                result.getClassSubjectId(),
                result.getSubjectCode(),
                result.getSubjectName(),
                result.getCoefficient(),
                result.getAverageScore(),
                result.getClassSubjectAverage(),
                result.getRankInSubject(),
                result.getEvaluationCount(),
                result.getTeacherAppreciation(),
                result.getAppreciatedBy(),
                result.getAppreciatedAt());
    }
}
