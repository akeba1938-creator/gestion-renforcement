package com.ecole.saas.shared.application;

import java.util.Locale;

/**
 * Utilitaire partagé d'échappement des jokers SQL {@code LIKE} ({@code %}, {@code _}, {@code \})
 * à associer systématiquement au caractère d'échappement {@link #ESCAPE_CHAR}
 * ({@code cb.like(expr, pattern, SqlLikePatterns.ESCAPE_CHAR)}).
 */
public final class SqlLikePatterns {

    public static final char ESCAPE_CHAR = '\\';

    private SqlLikePatterns() {
    }

    /**
     * Échappe les caractères spéciaux {@code \}, {@code %} et {@code _} pour une clause SQL {@code LIKE}.
     */
    public static String escape(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ESCAPE_CHAR || c == '%' || c == '_') {
                escaped.append(ESCAPE_CHAR);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    /**
     * Construit un motif {@code %terme%} normalisé en minuscules et échappé, ou {@code null} si vide.
     */
    public static String containsLower(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return "%" + escape(raw.trim().toLowerCase(Locale.ROOT)) + "%";
    }
}
