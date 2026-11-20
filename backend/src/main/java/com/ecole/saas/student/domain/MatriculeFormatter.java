package com.ecole.saas.student.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Formateur et validateur des matricules élèves au format {@code CODE_ECOLE-ANNEE-NUMERO}.
 */
public final class MatriculeFormatter {

    public static final int MIN_YEAR = 2000;
    public static final int MAX_YEAR = 2100;
    private static final Pattern SCHOOL_CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,31}$");
    private static final Pattern MATRICULE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,63}$");

    private MatriculeFormatter() {
    }

    public static String format(String schoolCode, int year, long sequenceNumber) {
        if (schoolCode == null || schoolCode.isBlank()) {
            throw new IllegalArgumentException("Le code d'établissement est obligatoire pour générer un matricule.");
        }
        String normalizedCode = schoolCode.trim().toUpperCase(Locale.ROOT);
        if (!SCHOOL_CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new IllegalArgumentException("Le code d'établissement est invalide pour la génération du matricule.");
        }
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new IllegalArgumentException(
                    "L'année de matricule doit être comprise entre " + MIN_YEAR + " et " + MAX_YEAR + ".");
        }
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("Le numéro séquentiel de matricule doit être strictement positif.");
        }
        String padded = String.format(Locale.ROOT, "%04d", sequenceNumber);
        String matricule = normalizedCode + "-" + year + "-" + padded;
        if (matricule.length() > 64) {
            throw new IllegalArgumentException("Le matricule généré dépasse la longueur maximale de 64 caractères.");
        }
        return matricule;
    }

    public static String normalize(String rawMatricule) {
        if (rawMatricule == null || rawMatricule.isBlank()) {
            throw new IllegalArgumentException("Le matricule est obligatoire.");
        }
        String normalized = rawMatricule.trim().toUpperCase(Locale.ROOT);
        if (!MATRICULE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Le format du matricule est invalide.");
        }
        return normalized;
    }
}
