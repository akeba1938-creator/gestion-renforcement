package com.ecole.saas.finance.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Arithmétique entière stricte en FCFA (XOF, sans décimales ni flottants) avec contrôle de débordement.
 */
public final class MoneyMath {

    public static final String CURRENCY_XOF = "XOF";

    private MoneyMath() {
    }

    public static long requireNonNegative(long amountXof, String fieldLabel) {
        if (amountXof < 0) {
            throw new IllegalArgumentException(fieldLabel + " doit être positif ou nul en FCFA.");
        }
        return amountXof;
    }

    public static long requirePositive(long amountXof, String fieldLabel) {
        if (amountXof <= 0) {
            throw new IllegalArgumentException(fieldLabel + " doit être strictement positif en FCFA.");
        }
        return amountXof;
    }

    public static long addExact(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Dépassement arithmétique sur le montant en FCFA.", ex);
        }
    }

    public static long subtractExact(long a, long b) {
        try {
            return Math.subtractExact(a, b);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Dépassement arithmétique sur le montant en FCFA.", ex);
        }
    }

    public static long multiplyExact(long unitAmountXof, int quantity) {
        requireNonNegative(unitAmountXof, "Le montant unitaire");
        if (quantity <= 0) {
            throw new IllegalArgumentException("La quantité doit être strictement positive.");
        }
        try {
            return Math.multiplyExact(unitAmountXof, (long) quantity);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Dépassement arithmétique sur le calcul de ligne en FCFA.", ex);
        }
    }

    /**
     * Calcule un pourcentage entier (1..100) d'un montant FCFA sans aucun flottant (division entière).
     */
    public static long percentageOf(long baseAmountXof, int percentage) {
        requireNonNegative(baseAmountXof, "Le montant de base");
        if (percentage < 1 || percentage > 100) {
            throw new IllegalArgumentException("Le pourcentage doit être compris entre 1 et 100.");
        }
        try {
            return Math.multiplyExact(baseAmountXof, (long) percentage) / 100L;
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Dépassement arithmétique lors du calcul de remise en pourcentage.", ex);
        }
    }

    /**
     * Répartit exactement un montant entier FCFA en {@code installmentCount} échéances entières.
     * Le reste de la division euclidienne est ajouté à la première échéance pour préserver la somme exacte.
     */
    public static List<Long> splitEvenly(long totalAmountXof, int installmentCount) {
        requirePositive(totalAmountXof, "Le montant total à répartir");
        if (installmentCount < 1 || installmentCount > 36) {
            throw new IllegalArgumentException("Le nombre d'échéances doit être compris entre 1 et 36.");
        }
        long baseShare = totalAmountXof / installmentCount;
        long remainder = totalAmountXof % installmentCount;
        List<Long> shares = new ArrayList<>(installmentCount);
        for (int i = 0; i < installmentCount; i++) {
            shares.add(i == 0 ? addExact(baseShare, remainder) : baseShare);
        }
        return shares;
    }
}
