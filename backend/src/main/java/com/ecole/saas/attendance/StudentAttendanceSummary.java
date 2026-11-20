package com.ecole.saas.attendance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Vue immuable de la synthèse d'assiduité d'un élève sur une période, une année ou un intervalle de dates.
 */
public record StudentAttendanceSummary(
        UUID studentId,
        UUID academicYearId,
        UUID evaluationPeriodId,
        LocalDate fromDate,
        LocalDate toDate,
        long totalSessions,
        long presentCount,
        long lateCount,
        long totalLateMinutes,
        long absentCount,
        long justifiedAbsenceCount,
        long unjustifiedAbsenceCount,
        long excusedCount,
        BigDecimal attendanceRatePercent) {
}
