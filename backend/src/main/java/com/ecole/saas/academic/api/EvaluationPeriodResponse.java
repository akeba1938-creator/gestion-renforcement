package com.ecole.saas.academic.api;

import com.ecole.saas.academic.domain.EvaluationPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EvaluationPeriodResponse(
        UUID id,
        UUID academicYearId,
        String code,
        String name,
        String periodType,
        int sequenceOrder,
        LocalDate startDate,
        LocalDate endDate,
        int weight,
        String status,
        Instant lockedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static EvaluationPeriodResponse from(EvaluationPeriod period) {
        return new EvaluationPeriodResponse(
                period.getId(),
                period.getAcademicYearId(),
                period.getCode(),
                period.getName(),
                period.getPeriodType().name(),
                period.getSequenceOrder(),
                period.getStartDate(),
                period.getEndDate(),
                period.getWeight(),
                period.getStatus().name(),
                period.getLockedAt(),
                period.getCreatedAt(),
                period.getUpdatedAt());
    }
}
