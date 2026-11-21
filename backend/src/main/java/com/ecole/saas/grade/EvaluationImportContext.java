package com.ecole.saas.grade;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Vue publique immuable d'une évaluation cible pour la validation à sec et l'import de notes.
 */
public record EvaluationImportContext(
        UUID evaluationId,
        UUID academicYearId,
        UUID evaluationPeriodId,
        String periodStatus,
        UUID classRoomId,
        UUID subjectId,
        String title,
        String status,
        BigDecimal maxScore,
        Set<UUID> existingGradedStudentIds) {
}
