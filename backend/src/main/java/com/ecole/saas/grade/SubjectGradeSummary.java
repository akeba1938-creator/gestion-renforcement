package com.ecole.saas.grade;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Vue immuable des résultats d'un élève dans une matière sur une période d'évaluation.
 */
public record SubjectGradeSummary(
        UUID subjectId,
        String subjectCode,
        String subjectName,
        int coefficient,
        BigDecimal averageScore,
        BigDecimal classSubjectAverage,
        Integer rankInSubject,
        int evaluationCount,
        String teacherAppreciation) {
}
