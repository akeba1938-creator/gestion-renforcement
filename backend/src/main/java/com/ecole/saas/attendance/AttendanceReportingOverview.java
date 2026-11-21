package com.ecole.saas.attendance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Vue consolidée immuable des indicateurs d'assiduité et d'absences exposée en lecture seule
 * aux modules aval (ex. {@code reporting}).
 */
public record AttendanceReportingOverview(
        UUID academicYearId,
        UUID evaluationPeriodId,
        LocalDate fromDate,
        LocalDate toDate,
        long sessionCount,
        long totalRecords,
        long presentCount,
        long lateCount,
        long totalLateMinutes,
        long absentCount,
        long justifiedAbsenceCount,
        long unjustifiedAbsenceCount,
        long excusedCount,
        BigDecimal attendanceRatePercent,
        List<ClassAttendanceReportingBreakdown> classBreakdowns) {
}
