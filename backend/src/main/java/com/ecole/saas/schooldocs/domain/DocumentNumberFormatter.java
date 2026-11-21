package com.ecole.saas.schooldocs.domain;

import java.util.Locale;

/**
 * Formateur déterministe de la numérotation officielle séquentielle des documents scolaires :
 * {@code DOC-<CODE_ECOLE>-<ANNEE>-NNNNNN}.
 */
public final class DocumentNumberFormatter {

    private static final String PREFIX = "DOC";

    private DocumentNumberFormatter() {
    }

    public static String format(String schoolCode, int sequenceYear, int sequenceNumber) {
        if (schoolCode == null || schoolCode.isBlank()) {
            throw new SchoolDocValidationException("Le code établissement est obligatoire pour numéroter le document.");
        }
        if (sequenceYear < 2000 || sequenceYear > 2100) {
            throw new SchoolDocValidationException("L'année de numérotation doit être comprise entre 2000 et 2100.");
        }
        if (sequenceNumber <= 0) {
            throw new SchoolDocValidationException("Le numéro séquentiel de document doit être strictement positif.");
        }
        String normalizedCode = schoolCode.trim().toUpperCase(Locale.ROOT);
        return String.format(Locale.ROOT, "%s-%s-%04d-%06d", PREFIX, normalizedCode, sequenceYear, sequenceNumber);
    }
}
