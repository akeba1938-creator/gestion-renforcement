package com.ecole.saas.schoolimport.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecole.saas.schoolimport.DuplicateStrategy;
import com.ecole.saas.schoolimport.ImportJobEventType;
import com.ecole.saas.schoolimport.ImportJobStatus;
import com.ecole.saas.schoolimport.ImportRowAction;
import com.ecole.saas.schoolimport.ImportRowStatus;
import com.ecole.saas.schoolimport.ImportSourceFormat;
import com.ecole.saas.schoolimport.ImportType;
import com.ecole.saas.schoolimport.ImportValidationMode;
import com.ecole.saas.schoolimport.domain.ImportFieldParser.FieldParseException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SchoolImportDomainPolicyTest {

    @Test
    void shouldDetectRealFileFormatAndRejectSpoofedOrUnsafeBinaries() {
        byte[] csvUtf8 = "first_name;last_name\nAwa;Ndiaye\n".getBytes(StandardCharsets.UTF_8);
        assertThat(ImportFileSecurityPolicy.detectAndValidateFormat(csvUtf8))
                .isEqualTo(ImportSourceFormat.CSV);

        byte[] zipHeader = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};
        assertThat(ImportFileSecurityPolicy.detectAndValidateFormat(zipHeader))
                .isEqualTo(ImportSourceFormat.XLSX);

        byte[] pdfBytes = "%PDF-1.4\nfake".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> ImportFileSecurityPolicy.detectAndValidateFormat(pdfBytes))
                .isInstanceOf(SchoolImportValidationException.class);

        byte[] exeBytes = new byte[] {'M', 'Z', 0x01, 0x02};
        assertThatThrownBy(() -> ImportFileSecurityPolicy.detectAndValidateFormat(exeBytes))
                .isInstanceOf(SchoolImportValidationException.class);

        byte[] nulBytes = new byte[] {'a', ';', 'b', 0x00, 'c'};
        assertThatThrownBy(() -> ImportFileSecurityPolicy.detectAndValidateFormat(nulBytes))
                .isInstanceOf(SchoolImportValidationException.class);

        byte[] invalidUtf8 = new byte[] {(byte) 0xFF, (byte) 0xFE, 0x41};
        assertThatThrownBy(() -> ImportFileSecurityPolicy.detectAndValidateFormat(invalidUtf8))
                .isInstanceOf(SchoolImportValidationException.class);

        assertThatThrownBy(() -> ImportFileSecurityPolicy.detectAndValidateFormat(new byte[0]))
                .isInstanceOf(SchoolImportValidationException.class);
    }

    @Test
    void shouldNeutralizeCsvFormulaInjectionAndSanitizeFileNames() {
        assertThat(ImportFileSecurityPolicy.neutralizeCsvFormula("=CMD|' /C calc'!A0"))
                .startsWith("'=CMD");
        assertThat(ImportFileSecurityPolicy.neutralizeCsvFormula("+221771234567"))
                .isEqualTo("'+221771234567");
        assertThat(ImportFileSecurityPolicy.neutralizeCsvFormula("-100")).isEqualTo("'-100");
        assertThat(ImportFileSecurityPolicy.neutralizeCsvFormula("@SUM(A1:A2)")).isEqualTo("'@SUM(A1:A2)");
        assertThat(ImportFileSecurityPolicy.neutralizeCsvFormula(" =CMD|' /C calc'!A0"))
                .isEqualTo("' =CMD|' /C calc'!A0");
        assertThat(ImportFileSecurityPolicy.neutralizeCsvFormula("\n=SUM(A1:A2)"))
                .isEqualTo("'\n=SUM(A1:A2)");
        assertThat(ImportFileSecurityPolicy.neutralizeCsvFormula("  +1+1"))
                .isEqualTo("'  +1+1");
        assertThat(ImportFileSecurityPolicy.neutralizeCsvFormula("\t@EVIL"))
                .isEqualTo("'\t@EVIL");
        assertThat(ImportFileSecurityPolicy.neutralizeCsvFormula("\0=EVIL"))
                .isEqualTo("'=EVIL");
        assertThat(ImportFileSecurityPolicy.neutralizeCsvFormula("Awa Ndiaye")).isEqualTo("Awa Ndiaye");

        assertThat(ImportFileSecurityPolicy.sanitizeFileName("../../evil<script>.csv", ImportSourceFormat.CSV))
                .isEqualTo("evil_script_.csv");
        assertThat(ImportFileSecurityPolicy.sanitizeFileName("eleves.csv", ImportSourceFormat.XLSX))
                .isEqualTo("eleves.xlsx");
    }

    @Test
    void shouldParseAndValidateDatesPhonesFcfaScoresAndEnums() {
        assertThat(ImportFieldParser.parseDate("2014-05-12", "birth_date", true))
                .isEqualTo(LocalDate.of(2014, 5, 12));
        assertThat(ImportFieldParser.parseDate("12/05/2014", "birth_date", true))
                .isEqualTo(LocalDate.of(2014, 5, 12));
        assertThatThrownBy(() -> ImportFieldParser.parseDate("32/13/2014", "birth_date", true))
                .isInstanceOf(FieldParseException.class);

        assertThat(ImportFieldParser.parsePhoneE164("+221 77 123 45 67", "phone", true))
                .isEqualTo("+221771234567");
        assertThatThrownBy(() -> ImportFieldParser.parsePhoneE164("771234567", "phone", true))
                .isInstanceOf(FieldParseException.class);

        assertThat(ImportFieldParser.parseOptionalAmountXof("5 000", "hourly_rate_xof")).isEqualTo(5000L);
        assertThatThrownBy(() -> ImportFieldParser.parseOptionalAmountXof("5000.50", "hourly_rate_xof"))
                .isInstanceOf(FieldParseException.class);
        assertThatThrownBy(() -> ImportFieldParser.parseOptionalAmountXof("-100", "hourly_rate_xof"))
                .isInstanceOf(FieldParseException.class);

        assertThat(ImportFieldParser.parseOptionalScore("15,5", "score", new BigDecimal("20.00")))
                .isEqualByComparingTo("15.50");
        assertThatThrownBy(() -> ImportFieldParser.parseOptionalScore("21", "score", new BigDecimal("20.00")))
                .isInstanceOf(FieldParseException.class);

        assertThat(ImportFieldParser.parseGender("F", "gender", true)).isEqualTo("FEMALE");
        assertThat(ImportFieldParser.parseGender("Garçon", "gender", true)).isEqualTo("MALE");
        assertThat(ImportFieldParser.parseOptionalBoolean("oui", "repeating")).isTrue();
        assertThat(ImportFieldParser.parseOptionalBoolean("non", "repeating")).isFalse();
        assertThat(ImportFieldParser.parseOptionalLateMinutes("15", "late_minutes")).isEqualTo(15);
        assertThatThrownBy(() -> ImportFieldParser.parseOptionalLateMinutes("0", "late_minutes"))
                .isInstanceOf(FieldParseException.class);
    }

    @Test
    void shouldResolveFrenchAndAccentedColumnAliasesAndMaskSensitivePii() {
        assertThat(ImportColumnCatalog.resolveCanonicalColumn(ImportType.STUDENTS, "Prénom"))
                .isEqualTo("first_name");
        assertThat(ImportColumnCatalog.resolveCanonicalColumn(ImportType.STUDENTS, "Date de naissance"))
                .isEqualTo("birth_date");
        assertThat(ImportColumnCatalog.resolveCanonicalColumn(ImportType.TEACHERS, "Taux horaire FCFA"))
                .isEqualTo("hourly_rate_xof");

        Map<String, String> rawRow = new LinkedHashMap<>();
        rawRow.put("guardian_first_name", "Fatou");
        rawRow.put("national_id_number", "1754198500123");
        rawRow.put("medical_notes", "Asthme sévère");
        String previewJson = ImportRowSanitizer.toSanitizedPreviewJson(rawRow);
        assertThat(previewJson).contains("\"guardian_first_name\":\"Fatou\"");
        assertThat(previewJson).contains("\"national_id_number\":\"[MASQUE]\"");
        assertThat(previewJson).contains("\"medical_notes\":\"[MASQUE]\"");
        assertThat(previewJson).doesNotContain("1754198500123").doesNotContain("Asthme");
    }

    @Test
    void shouldEnforceJobStateMachineAndAppendOnlyEntityImmutability() {
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-15T10:00:00Z");
        byte[] content = "first_name;last_name\nAwa;Ndiaye\n".getBytes(StandardCharsets.UTF_8);
        String sha = ImportFileSecurityPolicy.sha256Hex(content);

        ImportJob job = new ImportJob(
                ImportJobNumberFormatter.format("ECO", 2026, 1),
                "idem-1",
                sha,
                ImportType.STUDENTS,
                ImportSourceFormat.CSV,
                ImportValidationMode.STRICT_ALL_OR_NOTHING,
                DuplicateStrategy.FAIL_ON_DUPLICATE,
                ImportJobStatus.PREVIEW_READY,
                null, null, null,
                "eleves.csv",
                ImportFileSecurityPolicy.CSV_CONTENT_TYPE,
                "tenants/t1/schoolimport/2026/IMP-ECO-2026-000001.csv",
                content.length,
                sha,
                1, 1, 0, 0,
                "STUDENTS", 100L, 10L, 11L,
                actorId,
                now);

        assertThat(job.getJobNumber()).isEqualTo("IMP-ECO-2026-000001");
        job.beginProcessing("exec-1");
        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.PROCESSING);
        assertThatThrownBy(() -> job.cancel("Motif valide", actorId, now))
                .isInstanceOf(SchoolImportConflictException.class);

        job.completeExecution(ImportJobStatus.COMPLETED, 1, 0, 0, 1, 0, 0, actorId, now);
        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.COMPLETED);
        assertThatThrownBy(() -> job.beginProcessing("exec-2"))
                .isInstanceOf(SchoolImportConflictException.class);

        ImportJobFile immutableFile = new ImportJobFile(
                UUID.randomUUID(), "tenants/t1/schoolimport/2026/IMP-ECO-2026-000001.csv",
                ImportFileSecurityPolicy.CSV_CONTENT_TYPE, ImportSourceFormat.CSV, sha, content);
        assertThatThrownBy(immutableFile::onUpdate).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(immutableFile::onRemove).isInstanceOf(UnsupportedOperationException.class);

        ImportJobEvent immutableEvent = new ImportJobEvent(
                UUID.randomUUID(), 1, ImportJobEventType.UPLOADED, actorId, "Upload", now);
        assertThatThrownBy(immutableEvent::onUpdate).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(immutableEvent::onRemove).isInstanceOf(UnsupportedOperationException.class);

        ImportJobRow errRow = new ImportJobRow(
                UUID.randomUUID(), 2, "=EVIL()", ImportRowStatus.ERROR, ImportRowAction.ERROR,
                "INVALID_FIELD", "first_name", "=HYPERLINK(\"http://evil\")", null, null, "{}");
        String report = new String(ImportErrorReportFormatter.formatCsv(List.of(errRow)), StandardCharsets.UTF_8);
        assertThat(report).contains("'=EVIL()");
        assertThat(report).contains("'=HYPERLINK");
    }
}
