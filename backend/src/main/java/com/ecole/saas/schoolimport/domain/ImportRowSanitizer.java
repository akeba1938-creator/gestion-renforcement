package com.ecole.saas.schoolimport.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Assainit et masque systématiquement les PII sensibles (CNI/NIN, notes médicales, secrets)
 * avant toute persistance d'aperçu ligne en base ou exposition API.
 */
public final class ImportRowSanitizer {

    private static final String MASKED_VALUE = "[MASQUE]";
    private static final int MAX_VALUE_LEN = 120;
    private static final int MAX_JSON_LEN = 2000;

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "national_id_number",
            "cni",
            "nin",
            "passport",
            "numero_piece",
            "piece_identite",
            "medical_notes",
            "notes_medicales",
            "observations_medicales",
            "password",
            "secret",
            "token");

    private ImportRowSanitizer() {
    }

    public static String toSanitizedPreviewJson(Map<String, String> rowValues) {
        if (rowValues == null || rowValues.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : rowValues.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            String safeVal = isSensitiveKey(key) ? MASKED_VALUE : truncate(value.trim(), MAX_VALUE_LEN);
            String pair = (first ? "" : ",") + "\"" + escapeJson(key) + "\":\"" + escapeJson(safeVal) + "\"";
            if (sb.length() + pair.length() + 1 > MAX_JSON_LEN) {
                break;
            }
            sb.append(pair);
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.trim().toLowerCase(Locale.ROOT);
        if (SENSITIVE_KEYS.contains(lower)) {
            return true;
        }
        return lower.contains("cni")
                || lower.contains("nin")
                || lower.contains("national_id")
                || lower.contains("medical")
                || lower.contains("password")
                || lower.contains("secret")
                || lower.contains("token");
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private static String escapeJson(String input) {
        StringBuilder out = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(' ');
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
