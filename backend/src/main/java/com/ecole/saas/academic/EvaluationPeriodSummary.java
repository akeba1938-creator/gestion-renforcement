package com.ecole.saas.academic;

import java.time.LocalDate;
import java.util.UUID;

public record EvaluationPeriodSummary(
        UUID id,
        UUID academicYearId,
        String code,
        String name,
        String periodType,
        int sequenceOrder,
        LocalDate startDate,
        LocalDate endDate,
        int weight,
        String status) {
}
