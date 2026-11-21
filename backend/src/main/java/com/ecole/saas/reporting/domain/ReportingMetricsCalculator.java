package com.ecole.saas.reporting.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Calculateur pur (Java pur, sans Spring, sans flottants) des indicateurs consolidés
 * (taux de remplissage, taux de réussite, taux de recouvrement FCFA, validation de plage de dates).
 */
public final class ReportingMetricsCalculator {

    public static final long MAX_DATE_RANGE_DAYS = 366L;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ReportingMetricsCalculator() {
    }

    public static void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null) {
            if (fromDate.isAfter(toDate)) {
                throw new ReportingValidationException(
                        "reporting-invalid-date-range",
                        "La date de début (fromDate) doit être antérieure ou égale à la date de fin (toDate).");
            }
            long days = ChronoUnit.DAYS.between(fromDate, toDate);
            if (days > MAX_DATE_RANGE_DAYS) {
                throw new ReportingValidationException(
                        "reporting-date-range-too-wide",
                        "L'intervalle de dates ne doit pas dépasser " + MAX_DATE_RANGE_DAYS + " jours.");
            }
        }
    }

    /**
     * Calcule un pourcentage {@code (numerator / denominator) * 100} arrondi à 2 décimales {@code HALF_UP}.
     * Retourne {@code null} si le dénominateur est nul ou négatif.
     */
    public static BigDecimal percentage(long numerator, long denominator) {
        if (denominator <= 0L) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    /**
     * Additionne deux montants FCFA entiers avec détection de débordement arithmétique.
     */
    public static long addXof(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException ex) {
            throw new ReportingValidationException(
                    "reporting-amount-overflow",
                    "Dépassement arithmétique lors de l'agrégation d'un montant FCFA.");
        }
    }

    /**
     * Calcule le taux de recouvrement financier {@code (paidXof / netXof) * 100} sans flottants.
     * Retourne {@code null} lorsque {@code netXof <= 0}.
     */
    public static BigDecimal collectionRatePercent(long paidXof, long netXof) {
        if (netXof <= 0L) {
            return null;
        }
        return percentage(Math.max(0L, paidXof), netXof);
    }
}
