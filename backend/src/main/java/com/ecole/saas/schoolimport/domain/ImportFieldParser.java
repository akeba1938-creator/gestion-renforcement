package com.ecole.saas.schoolimport.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validateurs et normaliseurs purs domaine des champs tabulaires (dates, téléphones E.164,
 * montants FCFA entiers, notes {@link BigDecimal}, énumérations FR/EN, booléens).
 */
public final class ImportFieldParser {

    private static final DateTimeFormatter FRENCH_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);
    private static final Pattern E164_PHONE = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{0,49}$");

    private static final Set<String> VALID_ENROLLMENT_TYPES = Set.of("NEW_ADMISSION", "RE_ENROLLMENT", "TRANSFER_IN");
    private static final Set<String> VALID_TEACHER_TYPES = Set.of("PERMANENT", "PART_TIME", "TUTOR", "SUBSTITUTE");
    private static final Set<String> VALID_RELATIONSHIP_TYPES = Set.of(
            "FATHER", "MOTHER", "LEGAL_GUARDIAN", "GRANDPARENT", "UNCLE_AUNT", "SIBLING", "SPONSOR", "OTHER");
    private static final Set<String> VALID_GRADE_ATTENDANCE = Set.of(
            "PRESENT", "ABSENT_JUSTIFIED", "ABSENT_UNJUSTIFIED", "EXEMPTED");
    private static final Set<String> VALID_ATTENDANCE_STATUS = Set.of("PRESENT", "LATE", "ABSENT", "EXCUSED");

    private ImportFieldParser() {
    }

    public static String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new FieldParseException(fieldName, "REQUIRED_FIELD_MISSING", "Le champ '" + fieldName + "' est obligatoire.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new FieldParseException(
                    fieldName,
                    "FIELD_TOO_LONG",
                    "Le champ '" + fieldName + "' dépasse la longueur maximale de " + maxLength + " caractères.");
        }
        return trimmed;
    }

    public static String optionalText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new FieldParseException(
                    fieldName,
                    "FIELD_TOO_LONG",
                    "Le champ '" + fieldName + "' dépasse la longueur maximale de " + maxLength + " caractères.");
        }
        return trimmed;
    }

    public static String normalizeCode(String value, String fieldName, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new FieldParseException(
                        fieldName, "REQUIRED_FIELD_MISSING", "Le champ '" + fieldName + "' est obligatoire.");
            }
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new FieldParseException(
                    fieldName,
                    "INVALID_CODE_FORMAT",
                    "Le code '" + fieldName + "' doit contenir uniquement des lettres, chiffres, '-' ou '_'.");
        }
        return normalized;
    }

    public static String parsePhoneE164(String value, String fieldName, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new FieldParseException(
                        fieldName, "REQUIRED_FIELD_MISSING", "Le téléphone '" + fieldName + "' est obligatoire.");
            }
            return null;
        }
        String compact = value.trim().replaceAll("[\\s.-]", "");
        if (!E164_PHONE.matcher(compact).matches()) {
            throw new FieldParseException(
                    fieldName,
                    "INVALID_PHONE_E164",
                    "Le téléphone '" + fieldName + "' doit respecter le format international E.164 (ex. +221771234567).");
        }
        return compact;
    }

    public static String parseEmail(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 150 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new FieldParseException(
                    fieldName, "INVALID_EMAIL", "L'adresse e-mail fournie dans '" + fieldName + "' est invalide.");
        }
        return normalized;
    }

    public static LocalDate parseDate(String value, String fieldName, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new FieldParseException(
                        fieldName, "REQUIRED_FIELD_MISSING", "La date '" + fieldName + "' est obligatoire.");
            }
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.contains("/")) {
                return LocalDate.parse(trimmed, FRENCH_DATE);
            }
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ex) {
            throw new FieldParseException(
                    fieldName,
                    "INVALID_DATE",
                    "La date '" + fieldName + "' doit être au format YYYY-MM-DD ou DD/MM/YYYY.");
        }
    }

    public static String parseGender(String value, String fieldName, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new FieldParseException(
                        fieldName, "REQUIRED_FIELD_MISSING", "Le genre '" + fieldName + "' est obligatoire.");
            }
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "MALE", "M", "H", "HOMME", "MASCULIN", "GARCON", "GARÇON" -> "MALE";
            case "FEMALE", "F", "FEMME", "FEMININ", "FÉMININ", "FILLE" -> "FEMALE";
            case "OTHER", "AUTRE", "O" -> "OTHER";
            default -> throw new FieldParseException(
                    fieldName, "INVALID_GENDER", "Genre invalide (valeurs acceptées : MALE, FEMALE, OTHER, M, F).");
        };
    }

    public static String parseEnrollmentType(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return "NEW_ADMISSION";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!VALID_ENROLLMENT_TYPES.contains(normalized)) {
            throw new FieldParseException(
                    fieldName,
                    "INVALID_ENROLLMENT_TYPE",
                    "Type d'inscription invalide (NEW_ADMISSION, RE_ENROLLMENT ou TRANSFER_IN).");
        }
        return normalized;
    }

    public static String parseTeacherType(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new FieldParseException(
                    fieldName, "REQUIRED_FIELD_MISSING", "Le type d'enseignant '" + fieldName + "' est obligatoire.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!VALID_TEACHER_TYPES.contains(normalized)) {
            throw new FieldParseException(
                    fieldName,
                    "INVALID_TEACHER_TYPE",
                    "Type d'enseignant invalide (PERMANENT, PART_TIME, TUTOR ou SUBSTITUTE).");
        }
        return normalized;
    }

    public static String parseRelationshipType(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return "LEGAL_GUARDIAN";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        normalized = switch (normalized) {
            case "PERE", "PÈRE" -> "FATHER";
            case "MERE", "MÈRE" -> "MOTHER";
            case "TUTEUR", "TUTEUR_LEGAL", "TUTEUR_LÉGAL" -> "LEGAL_GUARDIAN";
            default -> normalized;
        };
        if (!VALID_RELATIONSHIP_TYPES.contains(normalized)) {
            throw new FieldParseException(
                    fieldName,
                    "INVALID_RELATIONSHIP_TYPE",
                    "Lien de parenté invalide (FATHER, MOTHER, LEGAL_GUARDIAN, GRANDPARENT, UNCLE_AUNT, SIBLING, SPONSOR, OTHER).");
        }
        return normalized;
    }

    public static String parseGradeAttendanceStatus(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return "PRESENT";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!VALID_GRADE_ATTENDANCE.contains(normalized)) {
            throw new FieldParseException(
                    fieldName,
                    "INVALID_GRADE_ATTENDANCE_STATUS",
                    "Statut de présence invalide (PRESENT, ABSENT_UNJUSTIFIED, ABSENT_JUSTIFIED, EXEMPTED).");
        }
        return normalized;
    }

    public static String parseAttendanceStatus(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new FieldParseException(
                    fieldName, "REQUIRED_FIELD_MISSING", "Le statut de présence '" + fieldName + "' est obligatoire.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        normalized = switch (normalized) {
            case "PRÉSENT" -> "PRESENT";
            case "RETARD" -> "LATE";
            case "EXCUSE", "EXCUSÉ", "JUSTIFIE", "JUSTIFIÉ" -> "EXCUSED";
            default -> normalized;
        };
        if (!VALID_ATTENDANCE_STATUS.contains(normalized)) {
            throw new FieldParseException(
                    fieldName,
                    "INVALID_ATTENDANCE_STATUS",
                    "Statut de pointage invalide (PRESENT, LATE, ABSENT ou EXCUSED).");
        }
        return normalized;
    }

    public static Boolean parseOptionalBoolean(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "oui", "yes", "o", "y", "vrai" -> Boolean.TRUE;
            case "false", "0", "non", "no", "n", "faux" -> Boolean.FALSE;
            default -> throw new FieldParseException(
                    fieldName, "INVALID_BOOLEAN", "Valeur booléenne invalide pour '" + fieldName + "' (true/false ou oui/non).");
        };
    }

    /**
     * Analyse un montant FCFA strictement entier ({@code long >= 0}, zéro décimale ni flottant).
     */
    public static Long parseOptionalAmountXof(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String compact = value.trim().replaceAll("\\s+", "");
        if (!compact.matches("^[0-9]+$")) {
            throw new FieldParseException(
                    fieldName,
                    "INVALID_AMOUNT_XOF",
                    "Le montant FCFA '" + fieldName + "' doit être un entier positif ou nul sans décimales.");
        }
        try {
            long parsed = Long.parseLong(compact);
            if (parsed < 0L) {
                throw new FieldParseException(
                        fieldName, "INVALID_AMOUNT_XOF", "Le montant FCFA '" + fieldName + "' ne peut pas être négatif.");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new FieldParseException(
                    fieldName, "INVALID_AMOUNT_XOF", "Le montant FCFA '" + fieldName + "' dépasse la capacité entière.");
        }
    }

    public static BigDecimal parseOptionalScore(String value, String fieldName, BigDecimal maxScore) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace(',', '.');
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new FieldParseException(
                    fieldName, "INVALID_SCORE", "La note '" + fieldName + "' n'est pas un nombre décimal valide.");
        }
        if (parsed.compareTo(BigDecimal.ZERO) < 0) {
            throw new FieldParseException(
                    fieldName, "SCORE_OUT_OF_RANGE", "La note ne peut pas être négative.");
        }
        if (maxScore != null && parsed.compareTo(maxScore) > 0) {
            throw new FieldParseException(
                    fieldName,
                    "SCORE_OUT_OF_RANGE",
                    "La note (" + parsed + ") dépasse le barème maximal de l'évaluation (" + maxScore + ").");
        }
        return parsed;
    }

    public static Integer parseOptionalLateMinutes(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.matches("^[0-9]+$")) {
            throw new FieldParseException(
                    fieldName, "INVALID_LATE_MINUTES", "Les minutes de retard doivent être un entier compris entre 1 et 300.");
        }
        try {
            int minutes = Integer.parseInt(trimmed);
            if (minutes < 1 || minutes > 300) {
                throw new FieldParseException(
                        fieldName, "INVALID_LATE_MINUTES", "Les minutes de retard doivent être comprises entre 1 et 300.");
            }
            return minutes;
        } catch (NumberFormatException ex) {
            throw new FieldParseException(
                    fieldName, "INVALID_LATE_MINUTES", "Les minutes de retard doivent être comprises entre 1 et 300.");
        }
    }

    public static final class FieldParseException extends RuntimeException {
        private final String fieldName;
        private final String errorCode;

        public FieldParseException(String fieldName, String errorCode, String message) {
            super(message);
            this.fieldName = fieldName;
            this.errorCode = errorCode;
        }

        public String fieldName() {
            return fieldName;
        }

        public String errorCode() {
            return errorCode;
        }
    }
}
