package com.ecole.saas.grade.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcul des mentions proportionnellement au barème de l'établissement (10, 20 ou 100).
 */
public final class MentionPolicy {

    private static final BigDecimal TWENTY = BigDecimal.valueOf(20);
    private static final BigDecimal THRESHOLD_EXCELLENT = BigDecimal.valueOf(18);
    private static final BigDecimal THRESHOLD_TRES_BIEN = BigDecimal.valueOf(16);
    private static final BigDecimal THRESHOLD_BIEN = BigDecimal.valueOf(14);
    private static final BigDecimal THRESHOLD_ASSEZ_BIEN = BigDecimal.valueOf(12);
    private static final BigDecimal THRESHOLD_PASSABLE = BigDecimal.valueOf(10);
    private static final BigDecimal THRESHOLD_INSUFFISANT = BigDecimal.valueOf(8);

    private MentionPolicy() {
    }

    public static Mention resolve(BigDecimal average, int gradingScale) {
        if (average == null) {
            return Mention.NONE;
        }
        if (gradingScale != 10 && gradingScale != 20 && gradingScale != 100) {
            throw new GradeValidationException("Barème de notation non pris en charge : " + gradingScale + ".");
        }
        BigDecimal equivalentOn20 = average
                .multiply(TWENTY)
                .divide(BigDecimal.valueOf(gradingScale), 6, RoundingMode.HALF_UP);
        if (equivalentOn20.compareTo(THRESHOLD_EXCELLENT) >= 0) {
            return Mention.EXCELLENT;
        }
        if (equivalentOn20.compareTo(THRESHOLD_TRES_BIEN) >= 0) {
            return Mention.TRES_BIEN;
        }
        if (equivalentOn20.compareTo(THRESHOLD_BIEN) >= 0) {
            return Mention.BIEN;
        }
        if (equivalentOn20.compareTo(THRESHOLD_ASSEZ_BIEN) >= 0) {
            return Mention.ASSEZ_BIEN;
        }
        if (equivalentOn20.compareTo(THRESHOLD_PASSABLE) >= 0) {
            return Mention.PASSABLE;
        }
        if (equivalentOn20.compareTo(THRESHOLD_INSUFFISANT) >= 0) {
            return Mention.INSUFFISANT;
        }
        return Mention.MEDIOCRE;
    }
}
