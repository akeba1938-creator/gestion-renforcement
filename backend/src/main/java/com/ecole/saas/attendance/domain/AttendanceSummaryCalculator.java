package com.ecole.saas.attendance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Moteur de calcul pur (Java pur, sans Spring) des synthèses et taux d'assiduité.
 */
public final class AttendanceSummaryCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private AttendanceSummaryCalculator() {
    }

    public record AttendanceMetrics(
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

    public static AttendanceMetrics compute(Collection<AttendanceRecord> records) {
        if (records == null || records.isEmpty()) {
            return new AttendanceMetrics(0, 0, 0, 0, 0, 0, 0, 0, null);
        }
        long total = 0;
        long present = 0;
        long late = 0;
        long lateMinutes = 0;
        long absent = 0;
        long justifiedAbsent = 0;
        long unjustifiedAbsent = 0;
        long excused = 0;

        for (AttendanceRecord record : records) {
            if (record == null) {
                continue;
            }
            total++;
            switch (record.getStatus()) {
                case PRESENT -> present++;
                case LATE -> {
                    late++;
                    if (record.getLateMinutes() != null) {
                        lateMinutes = Math.addExact(lateMinutes, record.getLateMinutes());
                    }
                }
                case ABSENT -> {
                    absent++;
                    if (record.isJustified()) {
                        justifiedAbsent++;
                    } else {
                        unjustifiedAbsent++;
                    }
                }
                case EXCUSED -> excused++;
            }
        }

        if (total == 0) {
            return new AttendanceMetrics(0, 0, 0, 0, 0, 0, 0, 0, null);
        }

        BigDecimal attended = BigDecimal.valueOf(present + late);
        BigDecimal rate = attended
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new AttendanceMetrics(
                total,
                present,
                late,
                lateMinutes,
                absent,
                justifiedAbsent,
                unjustifiedAbsent,
                excused,
                rate);
    }
}
