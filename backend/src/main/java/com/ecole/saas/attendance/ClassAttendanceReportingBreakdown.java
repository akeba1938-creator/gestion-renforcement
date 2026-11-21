package com.ecole.saas.attendance;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Vue consolidée immuable de l'assiduité d'une classe (séances, présences, retards et absences).
 */
public record ClassAttendanceReportingBreakdown(
        UUID classRoomId,
        long sessionCount,
        long totalRecords,
        long presentCount,
        long lateCount,
        long totalLateMinutes,
        long absentCount,
        long justifiedAbsenceCount,
        long unjustifiedAbsenceCount,
        long excusedCount,
        BigDecimal attendanceRatePercent) {
}
