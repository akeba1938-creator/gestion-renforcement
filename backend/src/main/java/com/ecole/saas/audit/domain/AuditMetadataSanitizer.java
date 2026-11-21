package com.ecole.saas.audit.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Politique pure d'assainissement des métadonnées et textes d'audit.
 * Garantit qu'aucun secret, mot de passe, jeton, cookie, numéro de pièce d'identité
 * ni contenu brut non masqué n'est persisté dans les journaux d'audit.
 */
public final class AuditMetadataSanitizer {

    public static final String MASK = "[MASQUE]";
    public static final int MAX_METADATA_ENTRIES = 25;
    public static final int MAX_VALUE_LENGTH = 200;
    public static final int MAX_JSON_LENGTH = 4096;
    public static final int MAX_SUMMARY_LENGTH = 500;

    private static final Pattern VALID_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{1,64}$");
    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile("[\\r\\n\\t\\u0000-\\u001F\\u007F]+");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s{2,}");

    private static final Pattern JWT_PATTERN =
            Pattern.compile("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}");
    private static final Pattern OTPAUTH_PATTERN = Pattern.compile("(?i)otpauth://\\S+");
    private static final Pattern AUTH_SCHEME_PATTERN =
            Pattern.compile("(?i)\\b(?:bearer|basic)\\s+[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern INLINE_SECRET_PATTERN = Pattern.compile(
            "(?i)\\b(?:password|passwd|pwd|secret|token|jwt|api[_-]?key|private[_-]?key|totp|otp|cookie|authorization"
                    + "|cni|nin|passeport|passport|numero[_ -]?piece|piece[_ -]?identite|iban|cvv)\\s*[:=#-]\\s*\\S+");
    private static final Pattern ID_DOC_SPACED_PATTERN =
            Pattern.compile("(?i)\\b(?:cni|nin|passeport|passport)\\s+[A-Z0-9][A-Z0-9-]{5,24}\\b");
    private static final Pattern SENEGAL_NIN_PATTERN =
            Pattern.compile("\\b[12]\\s?\\d{3}\\s?\\d{4}\\s?\\d{5}\\b");
    private static final Pattern UUID_EXACT_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern OPAQUE_SECRET_PATTERN =
            Pattern.compile("(?<![A-Za-z0-9+/=_-])[A-Za-z0-9+/=_-]{40,}(?![A-Za-z0-9+/=_-])");

    private static final Set<String> FORBIDDEN_TOKENS = Set.of(
            "password",
            "passwd",
            "pwd",
            "secret",
            "token",
            "jwt",
            "bearer",
            "cookie",
            "authorization",
            "credential",
            "credentials",
            "totp",
            "otp",
            "otpauth",
            "pin",
            "cni",
            "nin",
            "ssn",
            "passport",
            "passeport",
            "cvv",
            "iban",
            "hash",
            "ciphertext",
            "encrypted");

    private static final List<String> FORBIDDEN_COMPOUND_SUBSTRINGS = List.of(
            "password",
            "passwd",
            "secret",
            "token",
            "cookie",
            "credential",
            "authorization",
            "privatekey",
            "apikey",
            "encryptionkey",
            "otpauth",
            "passport",
            "passeport",
            "pieceidentite",
            "numeropiece",
            "nationalid",
            "identitynumber",
            "identitydoc",
            "cardnumber",
            "rawbody",
            "rawpayload",
            "rawcontent",
            "rawrequest",
            "rawresponse",
            "verificationtoken");

    private AuditMetadataSanitizer() {
    }

    public static boolean isForbiddenKey(String key) {
        if (key == null || key.isBlank() || !VALID_KEY_PATTERN.matcher(key).matches()) {
            return true;
        }
        String collapsed = key.toLowerCase(Locale.ROOT).replaceAll("[_.-]", "");
        for (String compound : FORBIDDEN_COMPOUND_SUBSTRINGS) {
            if (collapsed.contains(compound)) {
                return true;
            }
        }
        for (String token : splitKeyTokens(key)) {
            if (FORBIDDEN_TOKENS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    public static String sanitizeText(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String cleaned = CONTROL_CHARS_PATTERN.matcher(raw).replaceAll(" ");
        cleaned = JWT_PATTERN.matcher(cleaned).replaceAll(MASK);
        cleaned = OTPAUTH_PATTERN.matcher(cleaned).replaceAll(MASK);
        cleaned = AUTH_SCHEME_PATTERN.matcher(cleaned).replaceAll(MASK);
        cleaned = INLINE_SECRET_PATTERN.matcher(cleaned).replaceAll(MASK);
        cleaned = ID_DOC_SPACED_PATTERN.matcher(cleaned).replaceAll(MASK);
        cleaned = SENEGAL_NIN_PATTERN.matcher(cleaned).replaceAll(MASK);
        cleaned = maskOpaqueSecrets(cleaned);
        cleaned = MULTI_SPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim();
        if (cleaned.length() > maxLength) {
            return cleaned.substring(0, maxLength);
        }
        return cleaned;
    }

    public static Map<String, String> sanitizeMap(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new TreeMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            if (sanitized.size() >= MAX_METADATA_ENTRIES) {
                break;
            }
            String key = entry.getKey();
            Object rawValue = entry.getValue();
            if (key == null || rawValue == null) {
                continue;
            }
            String trimmedKey = key.trim();
            if (isForbiddenKey(trimmedKey)) {
                continue;
            }
            String formattedValue = sanitizeText(String.valueOf(rawValue), MAX_VALUE_LENGTH);
            if (formattedValue != null && !formattedValue.isEmpty()) {
                sanitized.put(trimmedKey, formattedValue);
            }
        }
        return Collections.unmodifiableMap(sanitized);
    }

    public static String toSanitizedJson(Map<String, ?> source) {
        Map<String, String> sanitized = sanitizeMap(source);
        if (sanitized.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : sanitized.entrySet()) {
            StringBuilder pair = new StringBuilder();
            if (!first) {
                pair.append(',');
            }
            appendJsonString(pair, entry.getKey());
            pair.append(':');
            appendJsonString(pair, entry.getValue());
            if (sb.length() + pair.length() + 1 > MAX_JSON_LENGTH) {
                break;
            }
            sb.append(pair);
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    public static Map<String, String> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        String trimmed = json.trim();
        if ("{}".equals(trimmed)) {
            return Map.of();
        }
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        int index = 1;
        int len = trimmed.length() - 1;
        while (index < len) {
            index = skipWhitespace(trimmed, index, len);
            if (index >= len) {
                break;
            }
            if (trimmed.charAt(index) != '"') {
                return Collections.unmodifiableMap(result);
            }
            ParsedString parsedKey = readJsonString(trimmed, index);
            if (parsedKey == null) {
                return Collections.unmodifiableMap(result);
            }
            index = skipWhitespace(trimmed, parsedKey.nextIndex(), len);
            if (index >= len || trimmed.charAt(index) != ':') {
                return Collections.unmodifiableMap(result);
            }
            index = skipWhitespace(trimmed, index + 1, len);
            if (index >= len || trimmed.charAt(index) != '"') {
                return Collections.unmodifiableMap(result);
            }
            ParsedString parsedValue = readJsonString(trimmed, index);
            if (parsedValue == null) {
                return Collections.unmodifiableMap(result);
            }
            result.put(parsedKey.value(), parsedValue.value());
            index = skipWhitespace(trimmed, parsedValue.nextIndex(), len);
            if (index < len && trimmed.charAt(index) == ',') {
                index++;
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static String maskOpaqueSecrets(String input) {
        Matcher matcher = OPAQUE_SECRET_PATTERN.matcher(input);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String candidate = matcher.group();
            if (!UUID_EXACT_PATTERN.matcher(candidate).matches() && hasMixedSecretShape(candidate)) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(MASK));
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(candidate));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static boolean hasMixedSecretShape(String candidate) {
        boolean hasDigit = false;
        boolean hasLetter = false;
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (Character.isLetter(c)) {
                hasLetter = true;
            }
            if (hasDigit && hasLetter) {
                return true;
            }
        }
        return false;
    }

    private static List<String> splitKeyTokens(String key) {
        String spaced = key.replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        String[] parts = spaced.split("[_.-]+");
        List<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isEmpty()) {
                tokens.add(part.toLowerCase(Locale.ROOT));
            }
        }
        return tokens;
    }

    private static void appendJsonString(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static int skipWhitespace(String s, int idx, int end) {
        while (idx < end && Character.isWhitespace(s.charAt(idx))) {
            idx++;
        }
        return idx;
    }

    private static ParsedString readJsonString(String s, int startQuote) {
        StringBuilder sb = new StringBuilder();
        int i = startQuote + 1;
        int len = s.length();
        while (i < len) {
            char c = s.charAt(i);
            if (c == '"') {
                return new ParsedString(sb.toString(), i + 1);
            }
            if (c == '\\') {
                i++;
                if (i >= len) {
                    return null;
                }
                char esc = s.charAt(i);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 >= len) {
                            return null;
                        }
                        String hex = s.substring(i + 1, i + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    }
                    default -> sb.append(esc);
                }
            } else {
                sb.append(c);
            }
            i++;
        }
        return null;
    }

    private record ParsedString(String value, int nextIndex) {
    }
}
