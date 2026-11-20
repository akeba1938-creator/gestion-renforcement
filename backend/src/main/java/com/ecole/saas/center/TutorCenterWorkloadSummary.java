package com.ecole.saas.center;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Synthèse immuable de la charge d'un intervenant / enseignant en mode centre et rémunération estimée en FCFA entiers.
 */
public record TutorCenterWorkloadSummary(
        UUID teacherId,
        LocalDate fromDate,
        LocalDate toDate,
        long activeGroupCount,
        long scheduledSessionCount,
        long completedSessionCount,
        long cancelledSessionCount,
        long completedMinutes,
        long estimatedRemunerationXof) {
}
