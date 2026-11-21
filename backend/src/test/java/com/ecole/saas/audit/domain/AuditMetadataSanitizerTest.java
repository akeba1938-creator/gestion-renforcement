package com.ecole.saas.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditMetadataSanitizerTest {

    @Test
    void shouldStripAllForbiddenSensitiveKeysAndPreserveSafeMetadata() {
        UUID studentId = UUID.randomUUID();
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("studentId", studentId.toString());
        raw.put("amountXof", 75_000L);
        raw.put("receiptNumber", "REC-IND-2026-000001");
        raw.put("password", "SuperSecret!2026");
        raw.put("passwordHash", "$argon2id$v=19$m=65536");
        raw.put("accessToken", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature123456");
        raw.put("refresh_token", "raw-refresh-token-value");
        raw.put("mfaSecret", "JBSWY3DPEHPK3PXP");
        raw.put("totpCode", "123456");
        raw.put("cni", "1990123456789");
        raw.put("nationalIdNumber", "SN-1990-99999");
        raw.put("numero_piece", "PASS-888777");
        raw.put("passport", "A12345678");
        raw.put("authorization", "Bearer abcdefghijklmnop");
        raw.put("cookie", "ecole_refresh_token=xyz");
        raw.put("rawBody", "{\"email\":\"a@b.sn\",\"password\":\"abc\"}");
        raw.put("verificationToken", "opaque-qr-token");
        raw.put("apiKey", "sk-live-secret-123");
        raw.put("iban", "SN08SN0120120100123456789012");

        Map<String, String> sanitized = AuditMetadataSanitizer.sanitizeMap(raw);

        assertThat(sanitized)
                .containsOnlyKeys("studentId", "amountXof", "receiptNumber")
                .containsEntry("studentId", studentId.toString())
                .containsEntry("amountXof", "75000")
                .containsEntry("receiptNumber", "REC-IND-2026-000001");
    }

    @Test
    void shouldNotRejectInnocentKeysContainingShortSubstrings() {
        Map<String, Object> raw = Map.of(
                "action", "PAYMENT_RECORDED",
                "joiningDate", "2026-10-01",
                "mappingCount", 3,
                "technicalCode", "T1");

        Map<String, String> sanitized = AuditMetadataSanitizer.sanitizeMap(raw);

        assertThat(sanitized).hasSize(4);
    }

    @Test
    void shouldMaskSensitivePatternsEmbeddedInValuesAndSummaryText() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMyJ9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        String otpUri = "otpauth://totp/EcoleSaaS:dir@ecole.sn?secret=JBSWY3DPEHPK3PXP&issuer=EcoleSaaS";
        String longOpaque = "aB3dE6gH9jK2mN5pQ8rS1tU4vW7xY0zA3bC6dE9fG2hI5";

        String text = "Erreur avec " + jwt
                + " et Bearer mySecretToken12345"
                + " et " + otpUri
                + " et password=MonMotDePasse"
                + " et CNI 1990123456789"
                + " et " + longOpaque;

        String cleaned = AuditMetadataSanitizer.sanitizeText(text, 500);

        assertThat(cleaned)
                .doesNotContain("eyJhbGci")
                .doesNotContain("mySecretToken12345")
                .doesNotContain("otpauth://")
                .doesNotContain("JBSWY3DPEHPK3PXP")
                .doesNotContain("MonMotDePasse")
                .doesNotContain("1990123456789")
                .doesNotContain(longOpaque)
                .contains(AuditMetadataSanitizer.MASK);
    }

    @Test
    void shouldPreserveUuidValuesAndPerformDeterministicJsonRoundTrip() {
        UUID id = UUID.randomUUID();
        Map<String, Object> raw = Map.of(
                "zKey", "valeur \"avec\" guillemets\\et\nligne",
                "aKey", id.toString(),
                "count", 42);

        String json = AuditMetadataSanitizer.toSanitizedJson(raw);
        Map<String, String> parsed = AuditMetadataSanitizer.parseJson(json);

        assertThat(json).startsWith("{\"aKey\":");
        assertThat(parsed)
                .containsEntry("aKey", id.toString())
                .containsEntry("count", "42")
                .containsEntry("zKey", "valeur \"avec\" guillemets\\et ligne");
    }

    @Test
    void shouldEnforceEntryCountAndLengthBounds() {
        Map<String, Object> oversized = new LinkedHashMap<>();
        for (int i = 0; i < 40; i++) {
            oversized.put("key_" + i, "v".repeat(300));
        }

        Map<String, String> sanitized = AuditMetadataSanitizer.sanitizeMap(oversized);

        assertThat(sanitized).hasSize(AuditMetadataSanitizer.MAX_METADATA_ENTRIES);
        assertThat(sanitized.values()).allSatisfy(v -> assertThat(v.length()).isLessThanOrEqualTo(200));
        assertThat(AuditMetadataSanitizer.toSanitizedJson(oversized).length())
                .isLessThanOrEqualTo(AuditMetadataSanitizer.MAX_JSON_LENGTH);
    }
}
