package com.ecole.saas.reporting.domain;

import com.ecole.saas.reporting.ReportFormat;
import com.ecole.saas.reporting.ReportType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Politique de sécurité, d'intégrité binaire SHA-256, d'idempotence et de nommage sûr en domaine pur.
 */
public final class ReportSecurityPolicy {

    public static final long MAX_SNAPSHOT_SIZE_BYTES = 5L * 1024L * 1024L;
    public static final long BYTES_PER_MIB = 1024L * 1024L;

    private ReportSecurityPolicy() {
    }

    public static String formatSnapshotNumber(String schoolCode, int year, int sequenceValue) {
        if (schoolCode == null || schoolCode.isBlank()) {
            throw new ReportingValidationException("Le code établissement est obligatoire.");
        }
        if (year < 2000 || year > 2100) {
            throw new ReportingValidationException("L'année du numéro de snapshot est invalide.");
        }
        if (sequenceValue <= 0) {
            throw new ReportingValidationException("La valeur de séquence doit être strictement positive.");
        }
        String normalizedCode = schoolCode.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_-]", "");
        if (normalizedCode.isEmpty()) {
            throw new ReportingValidationException("Le code établissement normalisé est invalide.");
        }
        return String.format("RPT-%s-%04d-%06d", normalizedCode, year, sequenceValue);
    }

    public static String normalizeIdempotencyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new ReportingValidationException(
                    "reporting-idempotency-key-required",
                    "La clé d'idempotence est obligatoire (en-tête Idempotency-Key ou champ idempotencyKey).");
        }
        String trimmed = rawKey.trim();
        if (trimmed.length() > 128) {
            throw new ReportingValidationException(
                    "reporting-idempotency-key-too-long",
                    "La clé d'idempotence ne doit pas dépasser 128 caractères.");
        }
        return trimmed;
    }

    public static String computeRequestHash(
            ReportType reportType,
            ReportFormat outputFormat,
            UUID academicYearId,
            UUID evaluationPeriodId,
            UUID classRoomId,
            UUID studentId,
            LocalDate fromDate,
            LocalDate toDate,
            String customTitle,
            UUID actorId) {
        String canonical = String.join(
                "|",
                Objects.requireNonNull(reportType, "Le type de rapport est obligatoire.").name(),
                Objects.requireNonNull(outputFormat, "Le format de sortie est obligatoire.").name(),
                academicYearId != null ? academicYearId.toString() : "",
                evaluationPeriodId != null ? evaluationPeriodId.toString() : "",
                classRoomId != null ? classRoomId.toString() : "",
                studentId != null ? studentId.toString() : "",
                fromDate != null ? fromDate.toString() : "",
                toDate != null ? toDate.toString() : "",
                customTitle != null ? customTitle.trim() : "",
                actorId != null ? actorId.toString() : "");
        return sha256Hex(canonical);
    }

    public static String sha256Hex(byte[] bytes) {
        if (bytes == null) {
            throw new ReportingValidationException("Le contenu binaire à hacher est obligatoire.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Algorithme SHA-256 indisponible.", ex);
        }
    }

    public static String sha256Hex(String value) {
        return sha256Hex(Objects.requireNonNull(value, "La chaîne à hacher est obligatoire.")
                .getBytes(StandardCharsets.UTF_8));
    }

    public static void assertValidPayload(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ReportingValidationException("Le contenu généré du rapport est vide.");
        }
        if (content.length > MAX_SNAPSHOT_SIZE_BYTES) {
            throw new ReportingValidationException(
                    "reporting-snapshot-too-large",
                    "La taille du rapport dépasse la limite maximale autorisée de 5 Mo.");
        }
    }

    public static void assertIntegrity(byte[] content, String expectedSha256) {
        assertValidPayload(content);
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            throw new ReportingValidationException("L'empreinte SHA-256 attendue est manquante.");
        }
        String actual = sha256Hex(content);
        if (!MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.US_ASCII),
                expectedSha256.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            throw new ReportingConflictException(
                    "reporting-snapshot-integrity-mismatch",
                    "La vérification d'intégrité SHA-256 du snapshot a échoué.");
        }
    }

    public static String buildSafeFileName(String baseName, ReportFormat format) {
        Objects.requireNonNull(format, "Le format de rapport est obligatoire.");
        if (baseName == null || baseName.isBlank()) {
            throw new ReportingValidationException("Le nom de base du fichier est obligatoire.");
        }
        String sanitized = baseName.trim()
                .replaceAll("[^A-Za-z0-9_-]", "_")
                .replaceAll("_+", "_");
        if (sanitized.isBlank() || sanitized.contains("..")) {
            throw new ReportingValidationException("Nom de fichier de rapport invalide.");
        }
        if (sanitized.length() > 160) {
            sanitized = sanitized.substring(0, 160);
        }
        return sanitized + "." + format.fileExtension();
    }

    public static String buildTenantScopedStorageKey(
            UUID tenantId, int year, String snapshotNumber, ReportFormat format) {
        Objects.requireNonNull(tenantId, "L'identifiant d'établissement est obligatoire.");
        if (year < 2000 || year > 2100) {
            throw new ReportingValidationException("L'année de stockage doit être comprise entre 2000 et 2100.");
        }
        String safeFileName = buildSafeFileName(snapshotNumber, format);
        String key = "tenants/" + tenantId + "/reporting/" + year + "/" + safeFileName;
        validateStorageObjectKey(key);
        return key;
    }

    public static void validateStorageObjectKey(String key) {
        if (key == null || key.isBlank() || key.length() > 300) {
            throw new ReportingValidationException("La clé de stockage du snapshot est invalide.");
        }
        if (key.contains("..") || key.contains("\\") || key.startsWith("/") || key.contains("\0")) {
            throw new ReportingValidationException("Clé de stockage non sécurisée détectée.");
        }
    }

    public static long bytesToCeilingMiB(long totalBytes) {
        if (totalBytes <= 0L) {
            return 0L;
        }
        long adjusted = Math.addExact(totalBytes, BYTES_PER_MIB - 1L);
        return adjusted / BYTES_PER_MIB;
    }
}
