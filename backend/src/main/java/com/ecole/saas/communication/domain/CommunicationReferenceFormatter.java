package com.ecole.saas.communication.domain;

import java.util.Locale;

/**
 * Formatage déterministe des références séquentielles de campagnes/messages : {@code MSG-<CODE_ECOLE>-<ANNEE>-NNNNNN}.
 */
public final class CommunicationReferenceFormatter {

    public static final int MIN_YEAR = 2000;
    public static final int MAX_YEAR = 2100;

    private CommunicationReferenceFormatter() {
    }

    public static String format(String schoolCode, int year, int sequenceValue) {
        if (schoolCode == null || schoolCode.isBlank()) {
            throw new CommunicationValidationException("Le code établissement est obligatoire.");
        }
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new CommunicationValidationException("L'année de séquence est hors bornes.");
        }
        if (sequenceValue <= 0) {
            throw new CommunicationValidationException("La valeur de séquence doit être strictement positive.");
        }
        String normalizedCode = schoolCode.trim().toUpperCase(Locale.ROOT);
        return String.format("MSG-%s-%04d-%06d", normalizedCode, year, sequenceValue);
    }
}
