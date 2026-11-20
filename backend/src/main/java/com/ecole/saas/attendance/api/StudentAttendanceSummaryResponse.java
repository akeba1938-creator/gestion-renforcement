package com.ecole.saas.attendance.api;

import com.ecole.saas.attendance.StudentAttendanceSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StudentAttendanceSummaryResponse(
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

    public static StudentAttendanceSummaryResponse from(StudentAttendanceSummary summary) {
        return new StudentAttendanceSummaryResponse(
                summary.studentId(),
                summary.academicYearId(),
                summary.evaluationPeriodId(),
                summary.fromDate(),
                summary.toDate(),
                summary.totalSessions(),
                summary.presentCount(),
                summary.lateCount(),
                summary.totalLateMinutes(),
                summary.absentCount(),
                summary.justifiedAbsenceCount(),
                summary.unjustifiedAbsenceCount(),
                summary.excusedCount(),
                summary.attendanceRatePercent());
    }
}
