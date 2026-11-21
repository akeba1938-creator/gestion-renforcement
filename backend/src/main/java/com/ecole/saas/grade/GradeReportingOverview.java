package com.ecole.saas.grade;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vue consolidée immuable des résultats académiques (moyennes, réussite, mentions et statuts bulletins)
 * exposée en lecture seule aux modules aval (ex. {@code reporting}).
 */
public record GradeReportingOverview(
        UUID academicYearId,
        UUID evaluationPeriodId,
        int gradingScale,
        int reportCardCount,
        int rankedStudentCount,
        int passedStudentCount,
        BigDecimal passRatePercent,
        BigDecimal overallAverage,
        BigDecimal minAverage,
        BigDecimal maxAverage,
        int draftCount,
        int validatedCount,
        int lockedCount,
        Map<String, Long> mentionCounts,
        List<ClassGradeReportingBreakdown> classBreakdowns) {
}
