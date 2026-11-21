package com.ecole.saas.schooldocs.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Politique de sécurité et d'intégrité en domaine pur pour les documents officiels :
 * - génération de jetons opaques de vérification (32 octets aléatoires Base64URL, zéro donnée personnelle) ;
 * - empreinte SHA-256 pour l'intégrité binaire et l'idempotence ;
 * - validation du type binaire réel PDF ({@code %PDF-}) et de la taille maximale (5 Mo) ;
 * - construction de noms de fichiers et de clés de stockage sûrs (anti-traversée de chemin).
 */
public final class DocumentSecurityPolicy {

    public static final String PDF_CONTENT_TYPE = "application/pdf";
    public static final long MAX_DOCUMENT_SIZE_BYTES = 5L * 1024L * 1024L;
    public static final long BYTES_PER_MIB = 1024L * 1024L;

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private DocumentSecurityPolicy() {
    }

    public static String generateOpaqueVerificationToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(byte[] bytes) {
        if (bytes == null) {
            throw new SchoolDocValidationException("Le contenu binaire à hacher est obligatoire.");
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

    public static void assertValidPdfBinary(byte[] content) {
        if (content == null || content.length < PDF_MAGIC.length) {
            throw new SchoolDocValidationException("Le fichier PDF généré est vide ou corrompu.");
        }
        if (content.length > MAX_DOCUMENT_SIZE_BYTES) {
            throw new SchoolDocValidationException(
                    "La taille du document PDF dépasse la limite autorisée de 5 Mo.");
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (content[i] != PDF_MAGIC[i]) {
                throw new SchoolDocValidationException(
                        "Le type réel du document ne correspond pas à un fichier PDF valide.");
            }
        }
    }

    public static void assertIntegrity(byte[] content, String expectedSha256) {
        assertValidPdfBinary(content);
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            throw new SchoolDocValidationException("L'empreinte SHA-256 attendue est manquante.");
        }
        String actual = sha256Hex(content);
        if (!MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.US_ASCII),
                expectedSha256.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            throw new SchoolDocConflictException(
                    "document-integrity-mismatch",
                    "L'intégrité binaire du document officiel a échoué lors de la vérification SHA-256.");
        }
    }

    public static String buildSafePdfFileName(String documentNumber) {
        if (documentNumber == null || documentNumber.isBlank()) {
            throw new SchoolDocValidationException("Le numéro de document est obligatoire.");
        }
        String sanitized = documentNumber.trim()
                .replaceAll("[^A-Za-z0-9_-]", "_")
                .replaceAll("_+", "_");
        if (sanitized.isBlank() || sanitized.contains("..")) {
            throw new SchoolDocValidationException("Nom de fichier documentaire invalide.");
        }
        if (sanitized.length() > 160) {
            sanitized = sanitized.substring(0, 160);
        }
        return sanitized + ".pdf";
    }

    public static String buildTenantScopedStorageKey(UUID tenantId, int year, String documentNumber) {
        Objects.requireNonNull(tenantId, "L'identifiant d'établissement est obligatoire.");
        if (year < 2000 || year > 2100) {
            throw new SchoolDocValidationException("L'année de stockage doit être comprise entre 2000 et 2100.");
        }
        String safeFileName = buildSafePdfFileName(documentNumber);
        String key = "tenants/" + tenantId + "/schooldocs/" + year + "/" + safeFileName;
        validateStorageObjectKey(key);
        return key;
    }

    public static void validateStorageObjectKey(String key) {
        if (key == null || key.isBlank() || key.length() > 300) {
            throw new SchoolDocValidationException("La clé de stockage du document est invalide.");
        }
        if (key.contains("..") || key.contains("\\") || key.startsWith("/") || key.contains("\0")) {
            throw new SchoolDocValidationException("Clé de stockage non sécurisée détectée.");
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
