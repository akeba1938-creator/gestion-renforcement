package com.ecole.saas.academic.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Règles métier pures de cohérence des périodes d'évaluation (bornes, absence de chevauchement,
 * conformité au système trimestriel/semestriel et découpage standard).
 */
public final class EvaluationPeriodPolicy {

    private static final int MAX_TRIMESTERS = 3;
    private static final int MAX_SEMESTERS = 2;

    private EvaluationPeriodPolicy() {
    }

    public static void validateWithinAcademicYear(AcademicYear year, LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(year, "L'année scolaire est obligatoire.");
        Objects.requireNonNull(startDate, "La date de début de période est obligatoire.");
        Objects.requireNonNull(endDate, "La date de fin de période est obligatoire.");
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException(
                    "La date de fin de la période doit être postérieure à la date de début.");
        }
        if (startDate.isBefore(year.getStartDate()) || endDate.isAfter(year.getEndDate())) {
            throw new IllegalArgumentException(
                    "Les dates de la période doivent être comprises dans celles de l'année scolaire.");
        }
    }

    public static void validateAgainstPeriodSystem(
            AcademicYear year,
            PeriodSystem periodType,
            int existingCountForYear) {
        Objects.requireNonNull(year, "L'année scolaire est obligatoire.");
        Objects.requireNonNull(periodType, "Le type de période est obligatoire.");
        PeriodSystem yearSystem = year.getPeriodSystem();
        if (yearSystem != PeriodSystem.CUSTOM && periodType != yearSystem) {
            throw new EvaluationPeriodConflictException(
                    "Le type de période (" + periodType + ") ne correspond pas au système de l'année scolaire ("
                            + yearSystem + ").");
        }
        if (yearSystem == PeriodSystem.TRIMESTER && existingCountForYear >= MAX_TRIMESTERS) {
            throw new EvaluationPeriodConflictException(
                    "Une année scolaire trimestrielle ne peut pas contenir plus de 3 trimestres.");
        }
        if (yearSystem == PeriodSystem.SEMESTER && existingCountForYear >= MAX_SEMESTERS) {
            throw new EvaluationPeriodConflictException(
                    "Une année scolaire semestrielle ne peut pas contenir plus de 2 semestres.");
        }
    }

    public static void validateNoOverlap(
            LocalDate candidateStart,
            LocalDate candidateEnd,
            List<EvaluationPeriod> existingPeriods,
            UUID excludePeriodId) {
        for (EvaluationPeriod existing : existingPeriods) {
            if (excludePeriodId != null && excludePeriodId.equals(existing.getId())) {
                continue;
            }
            boolean overlaps = !candidateEnd.isBefore(existing.getStartDate())
                    && !candidateStart.isAfter(existing.getEndDate());
            if (overlaps) {
                throw new EvaluationPeriodConflictException(
                        "Les dates de la période chevauchent la période existante « " + existing.getName() + " ».");
            }
        }
    }

    public static void validatePeriodsWithinNewYearBounds(
            LocalDate newYearStart,
            LocalDate newYearEnd,
            List<EvaluationPeriod> existingPeriods) {
        for (EvaluationPeriod period : existingPeriods) {
            if (period.getStartDate().isBefore(newYearStart) || period.getEndDate().isAfter(newYearEnd)) {
                throw new EvaluationPeriodConflictException(
                        "Les nouvelles dates de l'année scolaire excluent la période existante « "
                                + period.getName() + " ».");
            }
        }
    }

    public static List<EvaluationPeriod> buildStandardPeriods(AcademicYear year) {
        Objects.requireNonNull(year, "L'année scolaire est obligatoire.");
        PeriodSystem system = year.getPeriodSystem();
        int count;
        String codePrefix;
        String namePrefix;
        if (system == PeriodSystem.TRIMESTER) {
            count = MAX_TRIMESTERS;
            codePrefix = "T";
            namePrefix = "Trimestre ";
        } else if (system == PeriodSystem.SEMESTER) {
            count = MAX_SEMESTERS;
            codePrefix = "S";
            namePrefix = "Semestre ";
        } else {
            throw new IllegalArgumentException(
                    "La génération automatique n'est disponible que pour les systèmes TRIMESTER ou SEMESTER.");
        }

        long totalDays = ChronoUnit.DAYS.between(year.getStartDate(), year.getEndDate()) + 1;
        if (totalDays < count * 2L) {
            throw new IllegalArgumentException(
                    "La durée de l'année scolaire est trop courte pour générer automatiquement les périodes standard.");
        }

        List<EvaluationPeriod> periods = new ArrayList<>(count);
        LocalDate nextStart = year.getStartDate();
        for (int i = 0; i < count; i++) {
            int order = i + 1;
            LocalDate periodStart = nextStart;
            LocalDate periodEnd = (i == count - 1)
                    ? year.getEndDate()
                    : year.getStartDate().plusDays((totalDays * order) / count - 1);
            periods.add(new EvaluationPeriod(
                    year.getId(),
                    codePrefix + order,
                    namePrefix + order,
                    system,
                    order,
                    periodStart,
                    periodEnd,
                    EvaluationPeriod.DEFAULT_WEIGHT));
            nextStart = periodEnd.plusDays(1);
        }
        return periods;
    }
}
