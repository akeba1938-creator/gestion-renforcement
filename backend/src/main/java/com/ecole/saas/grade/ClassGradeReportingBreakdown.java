package com.ecole.saas.grade;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Vue immuable consolidée des indicateurs de notes et bulletins d'une classe pour une période ou année.
 */
public record ClassGradeReportingBreakdown(
        UUID classRoomId,
        int gradingScale,
        int reportCardCount,
        int rankedStudentCount,
        int passedStudentCount,
        BigDecimal passRatePercent,
        BigDecimal classAverage,
        BigDecimal minAverage,
        BigDecimal maxAverage,
        int draftCount,
        int validatedCount,
        int lockedCount,
        Map<String, Long> mentionCounts) {
}
