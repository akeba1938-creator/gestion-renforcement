package com.ecole.saas.grade.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Moteur de calcul pur (Java pur, sans Spring, sans flottants) des moyennes par matière,
 * moyennes générales pondérées par coefficient et statistiques de classe.
 */
public final class GradingCalculator {

    private static final int INTERMEDIATE_SCALE = 6;

    private GradingCalculator() {
    }

    public record EvaluationScoreInput(
            GradeAttendanceStatus attendanceStatus,
            BigDecimal rawScore,
            BigDecimal maxScore,
            int weight) {
    }

    public record SubjectAverageInput(
            int coefficient,
            BigDecimal averageScore) {
    }

    public record GeneralAverageResult(
            BigDecimal generalAverage,
            BigDecimal totalWeightedScore,
            int totalCoefficients) {
    }

    public record ClassAverageStats(
            BigDecimal classAverage,
            BigDecimal minAverage,
            BigDecimal maxAverage,
            int rankedCount) {
    }

    public static BigDecimal normalizeScore(BigDecimal rawScore, BigDecimal maxScore, int gradingScale) {
        Objects.requireNonNull(rawScore, "La note brute est obligatoire.");
        Objects.requireNonNull(maxScore, "Le barème de l'évaluation est obligatoire.");
        validateGradingScale(gradingScale);
        if (maxScore.compareTo(BigDecimal.ZERO) <= 0) {
            throw new GradeValidationException("Le barème de l'évaluation doit être strictement positif.");
        }
        if (rawScore.compareTo(BigDecimal.ZERO) < 0 || rawScore.compareTo(maxScore) > 0) {
            throw new GradeValidationException(
                    "La note doit être comprise entre 0 et le barème de l'évaluation (" + maxScore + ").");
        }
        BigDecimal targetScale = BigDecimal.valueOf(gradingScale);
        if (maxScore.compareTo(targetScale) == 0) {
            return rawScore.setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
        }
        return rawScore.multiply(targetScale).divide(maxScore, INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal computeSubjectAverage(
            List<EvaluationScoreInput> entries,
            int gradingScale,
            GradingPolicy policy) {
        validateGradingScale(gradingScale);
        Objects.requireNonNull(policy, "La politique de notation est obligatoire.");
        if (entries == null || entries.isEmpty()) {
            return null;
        }

        BigDecimal weightedSum = BigDecimal.ZERO;
        int totalWeight = 0;

        for (EvaluationScoreInput entry : entries) {
            if (entry == null || entry.attendanceStatus() == null) {
                continue;
            }
            if (entry.weight() <= 0) {
                throw new GradeValidationException("Le poids d'une évaluation doit être strictement positif.");
            }
            BigDecimal normalized = switch (entry.attendanceStatus()) {
                case PRESENT -> normalizeScore(entry.rawScore(), entry.maxScore(), gradingScale);
                case ABSENT_UNJUSTIFIED -> policy.unjustifiedAbsenceCountsAsZero() ? BigDecimal.ZERO : null;
                case ABSENT_JUSTIFIED -> policy.justifiedAbsenceCountsAsZero() ? BigDecimal.ZERO : null;
                case EXEMPTED -> null;
            };
            if (normalized != null) {
                weightedSum = weightedSum.add(normalized.multiply(BigDecimal.valueOf(entry.weight())));
                totalWeight = Math.addExact(totalWeight, entry.weight());
            }
        }

        if (totalWeight == 0) {
            return null;
        }
        return weightedSum.divide(
                BigDecimal.valueOf(totalWeight),
                policy.decimalScale(),
                policy.roundingMode());
    }

    public static GeneralAverageResult computeGeneralAverage(
            List<SubjectAverageInput> subjects,
            GradingPolicy policy) {
        Objects.requireNonNull(policy, "La politique de notation est obligatoire.");
        if (subjects == null || subjects.isEmpty()) {
            return new GeneralAverageResult(null, null, 0);
        }

        BigDecimal totalWeighted = BigDecimal.ZERO;
        int totalCoefficients = 0;

        for (SubjectAverageInput subject : subjects) {
            if (subject == null || subject.averageScore() == null) {
                continue;
            }
            if (subject.coefficient() <= 0) {
                throw new GradeValidationException("Le coefficient d'une matière doit être strictement positif.");
            }
            totalWeighted = totalWeighted.add(
                    subject.averageScore().multiply(BigDecimal.valueOf(subject.coefficient())));
            totalCoefficients = Math.addExact(totalCoefficients, subject.coefficient());
        }

        if (totalCoefficients == 0) {
            return new GeneralAverageResult(null, null, 0);
        }

        BigDecimal roundedTotal = totalWeighted.setScale(policy.decimalScale(), policy.roundingMode());
        BigDecimal generalAverage = totalWeighted.divide(
                BigDecimal.valueOf(totalCoefficients),
                policy.decimalScale(),
                policy.roundingMode());
        return new GeneralAverageResult(generalAverage, roundedTotal, totalCoefficients);
    }

    public static ClassAverageStats computeClassStats(Collection<BigDecimal> averages, GradingPolicy policy) {
        Objects.requireNonNull(policy, "La politique de notation est obligatoire.");
        if (averages == null || averages.isEmpty()) {
            return new ClassAverageStats(null, null, null, 0);
        }

        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min = null;
        BigDecimal max = null;
        int count = 0;

        for (BigDecimal avg : averages) {
            if (avg == null) {
                continue;
            }
            sum = sum.add(avg);
            if (min == null || avg.compareTo(min) < 0) {
                min = avg;
            }
            if (max == null || avg.compareTo(max) > 0) {
                max = avg;
            }
            count++;
        }

        if (count == 0) {
            return new ClassAverageStats(null, null, null, 0);
        }

        BigDecimal classAvg = sum.divide(BigDecimal.valueOf(count), policy.decimalScale(), policy.roundingMode());
        return new ClassAverageStats(
                classAvg,
                min.setScale(policy.decimalScale(), policy.roundingMode()),
                max.setScale(policy.decimalScale(), policy.roundingMode()),
                count);
    }

    public static void validateGradingScale(int gradingScale) {
        if (gradingScale != 10 && gradingScale != 20 && gradingScale != 100) {
            throw new GradeValidationException(
                    "Le barème de l'établissement doit être 10, 20 ou 100 (reçu : " + gradingScale + ").");
        }
    }
}
