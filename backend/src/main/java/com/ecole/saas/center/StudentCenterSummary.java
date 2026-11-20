package com.ecole.saas.center;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Synthèse immuable de l'activité centre d'un élève (cohortes, participation et crédits de séances).
 */
public record StudentCenterSummary(
        UUID studentId,
        LocalDate fromDate,
        LocalDate toDate,
        long activeGroupCount,
        long activePackCount,
        long remainingPackSessions,
        long totalSessionsRecorded,
        long presentCount,
        long lateCount,
        long totalLateMinutes,
        long absentCount,
        long excusedCount,
        BigDecimal attendanceRatePercent,
        BigDecimal averageParticipationRating,
        BigDecimal homeworkCompletionRatePercent) {
}
