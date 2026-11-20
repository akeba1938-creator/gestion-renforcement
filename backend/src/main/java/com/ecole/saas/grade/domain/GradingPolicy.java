package com.ecole.saas.grade.domain;

import java.math.RoundingMode;
import java.util.Objects;

/**
 * Règles configurables de calcul des moyennes (domaine pur sans Spring).
 */
public record GradingPolicy(
        boolean unjustifiedAbsenceCountsAsZero,
        boolean justifiedAbsenceCountsAsZero,
        int decimalScale,
        RoundingMode roundingMode) {

    public GradingPolicy {
        if (decimalScale < 0 || decimalScale > 4) {
            throw new GradeValidationException("La précision d'arrondi doit être comprise entre 0 et 4 décimales.");
        }
        Objects.requireNonNull(roundingMode, "Le mode d'arrondi est obligatoire.");
    }

    public static GradingPolicy defaultSenegalPolicy() {
        return new GradingPolicy(true, false, 2, RoundingMode.HALF_UP);
    }
}
