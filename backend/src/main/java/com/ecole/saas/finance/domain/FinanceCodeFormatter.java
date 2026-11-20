package com.ecole.saas.finance.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalisation des codes de frais et formatage des identifiants métier financiers
 * (numéros de facture et de reçu séquentiels isolés par établissement).
 */
public final class FinanceCodeFormatter {

    private static final Pattern FEE_CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,31}$");

    private FinanceCodeFormatter() {
    }

    public static String normalizeFeeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Le code de frais est obligatoire.");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!FEE_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Le code de frais doit contenir entre 2 et 32 caractères alphanumériques majuscules, '-' ou '_'.");
        }
        return normalized;
    }

    public static String formatInvoiceNumber(String schoolCode, int year, long sequenceValue) {
        return formatSequentialDocument("FAC", schoolCode, year, sequenceValue);
    }

    public static String formatReceiptNumber(String schoolCode, int year, long sequenceValue) {
        return formatSequentialDocument("REC", schoolCode, year, sequenceValue);
    }

    private static String formatSequentialDocument(String prefix, String schoolCode, int year, long sequenceValue) {
        if (schoolCode == null || schoolCode.isBlank()) {
            throw new IllegalArgumentException("Le code établissement est obligatoire.");
        }
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("L'année de numérotation est invalide.");
        }
        if (sequenceValue <= 0) {
            throw new IllegalArgumentException("Le numéro de séquence doit être strictement positif.");
        }
        String normalizedSchool = schoolCode.trim().toUpperCase(Locale.ROOT);
        return String.format(Locale.ROOT, "%s-%s-%d-%06d", prefix, normalizedSchool, year, sequenceValue);
    }
}
