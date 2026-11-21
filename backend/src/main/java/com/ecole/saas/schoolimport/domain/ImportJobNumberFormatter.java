package com.ecole.saas.schoolimport.domain;

import java.util.Locale;

/**
 * Formateur déterministe des numéros officiels de lots d'import : {@code IMP-<CODE_ECOLE>-<ANNEE>-NNNNNN}.
 */
public final class ImportJobNumberFormatter {

    public static final int MIN_YEAR = 2000;
    public static final int MAX_YEAR = 2100;

    private ImportJobNumberFormatter() {
    }

    public static String format(String schoolCode, int year, int sequenceValue) {
        if (schoolCode == null || schoolCode.isBlank()) {
            throw new SchoolImportValidationException("Le code établissement est requis pour la numérotation du lot.");
        }
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new SchoolImportValidationException("L'année de numérotation du lot d'import est invalide.");
        }
        if (sequenceValue <= 0) {
            throw new SchoolImportValidationException("La séquence de numérotation du lot doit être positive.");
        }
        String cleanCode = schoolCode.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "");
        if (cleanCode.isEmpty()) {
            throw new SchoolImportValidationException("Le code établissement est invalide.");
        }
        return String.format(Locale.ROOT, "IMP-%s-%d-%06d", cleanCode, year, sequenceValue);
    }
}
