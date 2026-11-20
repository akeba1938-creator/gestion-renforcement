package com.ecole.saas.grade;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Vue immuable des statistiques de notes d'une classe sur une période d'évaluation.
 */
public record ClassPeriodGradeStats(
        UUID classRoomId,
        UUID evaluationPeriodId,
        int gradingScale,
        int studentCount,
        int rankedStudentCount,
        BigDecimal classAverage,
        BigDecimal minAverage,
        BigDecimal maxAverage) {
}
