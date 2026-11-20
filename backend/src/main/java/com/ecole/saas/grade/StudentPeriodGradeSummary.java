package com.ecole.saas.grade;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Vue immuable de la synthèse de bulletin/notes d'un élève sur une période d'évaluation.
 */
public record StudentPeriodGradeSummary(
        UUID reportCardId,
        UUID academicYearId,
        UUID evaluationPeriodId,
        UUID classRoomId,
        UUID studentId,
        int gradingScale,
        BigDecimal generalAverage,
        BigDecimal classAverage,
        BigDecimal minClassAverage,
        BigDecimal maxClassAverage,
        Integer rankInClass,
        int classSize,
        boolean tiedRank,
        int totalCoefficients,
        String mention,
        String councilDecision,
        String homeroomAppreciation,
        String directorAppreciation,
        String status,
        List<SubjectGradeSummary> subjects) {
}
