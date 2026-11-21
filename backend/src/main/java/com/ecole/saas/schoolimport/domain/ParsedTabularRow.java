package com.ecole.saas.schoolimport.domain;

import java.util.Map;

/**
 * Ligne tabulaire normalisée issue d'un fichier CSV ou XLSX (numérotation à partir de la ligne 2).
 */
public record ParsedTabularRow(
        int rowNumber,
        Map<String, String> values) {

    public String get(String canonicalKey) {
        if (values == null || canonicalKey == null) {
            return null;
        }
        String raw = values.get(canonicalKey);
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
