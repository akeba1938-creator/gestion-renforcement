package com.ecole.saas.center.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/**
 * Calculs purs de suivi de participation des élèves et de charge/rémunération des intervenants en FCFA entiers.
 */
public final class CenterParticipationCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal SIXTY = new BigDecimal("60");

    private CenterParticipationCalculator() {
    }

    public record ParticipationStats(
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

    public record CompletedSessionWork(int durationMinutes, long effectiveHourlyRateXof) {
    }

    public static ParticipationStats computeParticipation(Collection<CenterSessionAttendance> attendances) {
        Objects.requireNonNull(attendances, "La liste de pointages est obligatoire.");
        long total = 0;
        long present = 0;
        long late = 0;
        long totalLateMinutes = 0;
        long absent = 0;
        long excused = 0;
        long ratingSum = 0;
        long ratingCount = 0;
        long homeworkEvaluated = 0;
        long homeworkDone = 0;

        for (CenterSessionAttendance record : attendances) {
            total++;
            switch (record.getStatus()) {
                case PRESENT -> present++;
                case LATE -> {
                    late++;
                    if (record.getLateMinutes() != null) {
                        totalLateMinutes = Math.addExact(totalLateMinutes, record.getLateMinutes());
                    }
                }
                case ABSENT -> absent++;
                case EXCUSED -> excused++;
            }
            if (record.getParticipationRating() != null) {
                ratingSum = Math.addExact(ratingSum, record.getParticipationRating());
                ratingCount++;
            }
            if (record.getHomeworkDone() != null) {
                homeworkEvaluated++;
                if (Boolean.TRUE.equals(record.getHomeworkDone())) {
                    homeworkDone++;
                }
            }
        }

        BigDecimal attendanceRate = total == 0
                ? null
                : BigDecimal.valueOf(present + late)
                        .multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        BigDecimal avgRating = ratingCount == 0
                ? null
                : BigDecimal.valueOf(ratingSum)
                        .divide(BigDecimal.valueOf(ratingCount), 2, RoundingMode.HALF_UP);

        BigDecimal homeworkRate = homeworkEvaluated == 0
                ? null
                : BigDecimal.valueOf(homeworkDone)
                        .multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(homeworkEvaluated), 2, RoundingMode.HALF_UP);

        return new ParticipationStats(
                total,
                present,
                late,
                totalLateMinutes,
                absent,
                excused,
                attendanceRate,
                avgRating,
                homeworkRate);
    }

    /**
     * Calcule la rémunération estimée en FCFA entiers (arrondi HALF_UP à l'entier FCFA, sans float ni double).
     */
    public static long computeEstimatedRemunerationXof(Collection<CompletedSessionWork> completedSessions) {
        Objects.requireNonNull(completedSessions, "La liste des séances réalisées est obligatoire.");
        long totalXof = 0L;
        for (CompletedSessionWork work : completedSessions) {
            if (work.durationMinutes() < 0 || work.effectiveHourlyRateXof() < 0L) {
                throw new CenterValidationException(
                        "center-remuneration-invalid",
                        "Calcul de rémunération invalide",
                        "La durée et le taux horaire FCFA doivent être positifs ou nuls.");
            }
            if (work.durationMinutes() == 0 || work.effectiveHourlyRateXof() == 0L) {
                continue;
            }
            long numerator = Math.multiplyExact((long) work.durationMinutes(), work.effectiveHourlyRateXof());
            long sessionXof = BigDecimal.valueOf(numerator)
                    .divide(SIXTY, 0, RoundingMode.HALF_UP)
                    .longValueExact();
            totalXof = Math.addExact(totalXof, sessionXof);
        }
        return totalXof;
    }
}
