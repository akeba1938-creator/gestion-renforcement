package com.ecole.saas.reporting.domain;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Générateur CSV UTF-8 conforme RFC 4180 avec protection systématique anti-CSV-Injection
 * (neutralisation de {@code =}, {@code +}, {@code -}, {@code @}, {@code \t}, {@code \r},
 * y compris précédés d'espaces ou de caractères de contrôle).
 */
public final class CsvFormulaSanitizer {

    private CsvFormulaSanitizer() {
    }

    public static String neutralizeFormula(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        }
        String sanitized = rawValue.replace("\0", "");
        if (sanitized.isEmpty()) {
            return "";
        }
        if (startsWithDangerousFormulaChar(sanitized)) {
            return "'" + sanitized;
        }
        return sanitized;
    }

    public static String formatCsvCell(String rawValue) {
        String safe = neutralizeFormula(rawValue);
        boolean mustQuote = safe.indexOf(',') >= 0
                || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0
                || safe.indexOf('\r') >= 0
                || safe.indexOf(';') >= 0;
        if (!mustQuote) {
            return safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    public static byte[] renderTable(List<String> headers, List<List<String>> rows) {
        if (headers == null || headers.isEmpty()) {
            throw new ReportingValidationException("Les en-têtes CSV sont obligatoires.");
        }
        StringBuilder sb = new StringBuilder();
        appendRow(sb, headers);
        if (rows != null) {
            for (List<String> row : rows) {
                if (row != null) {
                    appendRow(sb, row);
                }
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(formatCsvCell(cells.get(i)));
        }
        sb.append("\r\n");
    }

    private static boolean startsWithDangerousFormulaChar(String value) {
        char first = value.charAt(0);
        if (isFormulaTrigger(first)) {
            return true;
        }
        int idx = 0;
        while (idx < value.length() && (value.charAt(idx) == ' ' || value.charAt(idx) == '\t'
                || value.charAt(idx) == '\r' || value.charAt(idx) == '\n')) {
            if (value.charAt(idx) == '\t' || value.charAt(idx) == '\r') {
                return true;
            }
            idx++;
        }
        return idx < value.length() && isFormulaTrigger(value.charAt(idx));
    }

    private static boolean isFormulaTrigger(char c) {
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r';
    }
}
