package com.ecole.saas.schoolimport.domain;

import com.ecole.saas.schoolimport.ImportSourceFormat;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Politique de sécurité fichiers en domaine pur :
 * contrôle par type réel (octets magiques, jamais l'extension seule), limites de taille et de volume,
 * intégrité SHA-256, renommage sûr, stockage privé scopé par tenant et neutralisation CSV-Injection.
 */
public final class ImportFileSecurityPolicy {

    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    public static final long MAX_XLSX_UNCOMPRESSED_BYTES = 20L * 1024L * 1024L;
    public static final int MAX_XLSX_ENTRIES = 100;
    public static final int MAX_ROWS_PER_JOB = 2_000;
    public static final int MAX_COLUMNS_PER_ROW = 50;
    public static final int MAX_CELL_LENGTH = 1_000;
    public static final long BYTES_PER_MIB = 1024L * 1024L;

    public static final String CSV_CONTENT_TYPE = "text/csv; charset=UTF-8";
    public static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ImportFileSecurityPolicy() {
    }

    /**
     * Détecte et valide le format réel du fichier uniquement à partir des octets (jamais l'extension).
     */
    public static ImportSourceFormat detectAndValidateFormat(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new SchoolImportValidationException(
                    "schoolimport-empty-file",
                    "Fichier vide",
                    "Le fichier téléversé est vide.");
        }
        if (bytes.length > MAX_FILE_SIZE_BYTES) {
            throw new SchoolImportValidationException(
                    "schoolimport-file-too-large",
                    "Fichier trop volumineux",
                    "La taille du fichier dépasse la limite autorisée de 5 Mo.");
        }

        // Signature ZIP/OOXML (XLSX) : PK\x03\x04
        if (hasZipSignature(bytes)) {
            return ImportSourceFormat.XLSX;
        }

        // Rejet explicite des signatures binaires connues non autorisées
        if (hasForbiddenBinarySignature(bytes)) {
            throw new SchoolImportValidationException(
                    "schoolimport-unsupported-file-type",
                    "Type de fichier non autorisé",
                    "Le contenu binaire du fichier n'est ni un CSV UTF-8 valide ni un classeur Excel (.xlsx).");
        }

        // Rejet de tout octet NUL dans un fichier texte CSV
        for (byte b : bytes) {
            if (b == 0x00) {
                throw new SchoolImportValidationException(
                        "schoolimport-unsupported-file-type",
                        "Type de fichier non autorisé",
                        "Le fichier contient des octets binaires incompatibles avec un fichier CSV UTF-8.");
            }
        }

        // Validation stricte de l'encodage UTF-8 (avec BOM optionnel)
        decodeStrictUtf8(bytes);
        return ImportSourceFormat.CSV;
    }

    public static String decodeStrictUtf8(byte[] bytes) {
        int offset = 0;
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
        } catch (CharacterCodingException ex) {
            throw new SchoolImportValidationException(
                    "schoolimport-invalid-encoding",
                    "Encodage invalide",
                    "Le fichier CSV doit être encodé en UTF-8 valide.");
        }
    }

    public static String canonicalContentType(ImportSourceFormat format) {
        return format == ImportSourceFormat.XLSX ? XLSX_CONTENT_TYPE : CSV_CONTENT_TYPE;
    }

    public static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Algorithme SHA-256 indisponible.", ex);
        }
    }

    public static String sha256Hex(String value) {
        return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    public static long bytesToCeilingMiB(long totalBytes) {
        if (totalBytes <= 0L) {
            return 0L;
        }
        return (totalBytes + BYTES_PER_MIB - 1L) / BYTES_PER_MIB;
    }

    public static String sanitizeFileName(String rawFileName, ImportSourceFormat detectedFormat) {
        String extension = detectedFormat == ImportSourceFormat.XLSX ? ".xlsx" : ".csv";
        if (rawFileName == null || rawFileName.isBlank()) {
            return "import" + extension;
        }
        String base = rawFileName.replace('\\', '/');
        int lastSlash = base.lastIndexOf('/');
        if (lastSlash >= 0) {
            base = base.substring(lastSlash + 1);
        }
        base = base.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("_{2,}", "_");
        while (base.startsWith(".")) {
            base = base.substring(1);
        }
        int dotIndex = base.lastIndexOf('.');
        String stem = dotIndex > 0 ? base.substring(0, dotIndex) : base;
        if (stem.isBlank()) {
            stem = "import";
        }
        if (stem.length() > 140) {
            stem = stem.substring(0, 140);
        }
        return stem + extension;
    }

    public static String buildTenantScopedStorageKey(
            UUID tenantId, int year, String jobNumber, ImportSourceFormat detectedFormat) {
        String safeJob = jobNumber.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "");
        String ext = detectedFormat == ImportSourceFormat.XLSX ? "xlsx" : "csv";
        return "tenants/" + tenantId + "/schoolimport/" + year + "/" + safeJob + "." + ext;
    }

    public static String buildSafeCsvDownloadName(String prefix, String code) {
        String safePrefix = prefix.replaceAll("[^A-Za-z0-9_-]", "_");
        String safeCode = code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_");
        return safePrefix + "-" + safeCode + ".csv";
    }

    /**
     * Neutralise toute formule Excel/Calc potentiellement dangereuse dans une cellule CSV exportée.
     */
    public static String neutralizeCsvFormula(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        char first = raw.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + raw;
        }
        return raw;
    }

    public static String escapeCsvCell(String raw, char delimiter) {
        String safe = neutralizeCsvFormula(raw);
        boolean needsQuotes = safe.indexOf(delimiter) >= 0
                || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0
                || safe.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static boolean hasZipSignature(byte[] b) {
        return b.length >= 4
                && b[0] == 0x50
                && b[1] == 0x4B
                && b[2] == 0x03
                && b[3] == 0x04;
    }

    private static boolean hasForbiddenBinarySignature(byte[] b) {
        // %PDF-
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return true;
        }
        // PNG
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return true;
        }
        // JPEG
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return true;
        }
        // ELF / PE (MZ) / OLE2 legacy .xls (D0 CF 11 E0)
        if (b.length >= 4 && b[0] == 0x7F && b[1] == 'E' && b[2] == 'L' && b[3] == 'F') {
            return true;
        }
        if (b.length >= 2 && b[0] == 'M' && b[1] == 'Z') {
            return true;
        }
        if (b.length >= 4
                && (b[0] & 0xFF) == 0xD0
                && (b[1] & 0xFF) == 0xCF
                && (b[2] & 0xFF) == 0x11
                && (b[3] & 0xFF) == 0xE0) {
            return true;
        }
        // ZIP vide / spanned
        return b.length >= 4 && b[0] == 0x50 && b[1] == 0x4B;
    }
}
