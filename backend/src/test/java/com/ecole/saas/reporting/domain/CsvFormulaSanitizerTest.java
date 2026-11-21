package com.ecole.saas.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvFormulaSanitizerTest {

    @Test
    void shouldNeutralizeDangerousFormulaPrefixesAgainstCsvInjection() {
        assertThat(CsvFormulaSanitizer.neutralizeFormula("=SUM(A1:A10)")).isEqualTo("'=SUM(A1:A10)");
        assertThat(CsvFormulaSanitizer.neutralizeFormula("+cmd|' /C calc'!A0")).isEqualTo("'+cmd|' /C calc'!A0");
        assertThat(CsvFormulaSanitizer.neutralizeFormula("-2+3+cmd|' /C calc'!A0")).isEqualTo("'-2+3+cmd|' /C calc'!A0");
        assertThat(CsvFormulaSanitizer.neutralizeFormula("@SUM(1+1)*cmd|' /C calc'!A0"))
                .isEqualTo("'@SUM(1+1)*cmd|' /C calc'!A0");
        assertThat(CsvFormulaSanitizer.neutralizeFormula("\t=1+1")).isEqualTo("'\t=1+1");
        assertThat(CsvFormulaSanitizer.neutralizeFormula("\r=1+1")).isEqualTo("'\r=1+1");
        assertThat(CsvFormulaSanitizer.neutralizeFormula("   =HYPERLINK(\"http://evil.test\")"))
                .isEqualTo("'   =HYPERLINK(\"http://evil.test\")");
    }

    @Test
    void shouldLeaveNormalFrenchValuesIntactAndQuoteRfc4180Properly() {
        assertThat(CsvFormulaSanitizer.neutralizeFormula("École privée Dakar")).isEqualTo("École privée Dakar");
        assertThat(CsvFormulaSanitizer.neutralizeFormula("16.50")).isEqualTo("16.50");
        assertThat(CsvFormulaSanitizer.formatCsvCell("Diop, Awa \"Chef\"")).isEqualTo("\"Diop, Awa \"\"Chef\"\"\"");

        byte[] csvBytes = CsvFormulaSanitizer.renderTable(
                List.of("classe", "libelle", "montant_fcfa"),
                List.of(List.of("6E-A", "=HYPERLINK(\"http://evil\")", "100000")));
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csv).contains("classe,libelle,montant_fcfa\r\n");
        assertThat(csv).contains("6E-A,\"'=HYPERLINK(\"\"http://evil\"\")\",100000\r\n");
    }

    @Test
    void shouldRejectEmptyHeaders() {
        assertThatThrownBy(() -> CsvFormulaSanitizer.renderTable(List.of(), List.of()))
                .isInstanceOf(ReportingValidationException.class);
    }
}
