package com.ecole.saas.schoolimport.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecole.saas.schoolimport.ImportSourceFormat;
import com.ecole.saas.schoolimport.ImportType;
import com.ecole.saas.schoolimport.domain.ParsedTabularDataset;
import com.ecole.saas.schoolimport.domain.SchoolImportValidationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

public class SecureTabularFileParserTest {

    private final SecureTabularFileParser parser = new SecureTabularFileParser();

    @Test
    void shouldParseCsvWithUtf8BomSemicolonsQuotedFieldsAndFrenchAliases() {
        String csv = "\uFEFFPrénom;Nom;Sexe;Téléphone;Adresse\r\n"
                + "Awa;Ndiaye;F;+221771110001;\"Rue 10; Mermoz, \"\"Dakar\"\"\"\r\n"
                + "Moussa;Diop;M;+221771110002;Plateau\r\n";

        ParsedTabularDataset dataset = parser.parse(
                csv.getBytes(StandardCharsets.UTF_8), ImportType.STUDENTS, null);

        assertThat(dataset.detectedFormat()).isEqualTo(ImportSourceFormat.CSV);
        assertThat(dataset.normalizedHeaders())
                .containsExactly("first_name", "last_name", "gender", "phone", "address");
        assertThat(dataset.rows()).hasSize(2);
        assertThat(dataset.rows().get(0).rowNumber()).isEqualTo(2);
        assertThat(dataset.rows().get(0).get("address")).isEqualTo("Rue 10; Mermoz, \"Dakar\"");
        assertThat(dataset.rows().get(1).rowNumber()).isEqualTo(3);
        assertThat(dataset.rows().get(1).get("first_name")).isEqualTo("Moussa");
    }

    @Test
    void shouldParseValidOoxmlXlsxWithSharedStringsAndNumericCells() throws IOException {
        byte[] xlsx = buildMinimalXlsx(
                List.of("employee_code", "first_name", "last_name", "teacher_type", "phone", "hourly_rate_xof"),
                List.of(
                        List.of("ENS-01", "Cheikh", "Fall", "PERMANENT", "+221773330001", "5000"),
                        List.of("ENS-02", "Aminata", "Sarr", "TUTOR", "+221773330002", "7500")));

        ParsedTabularDataset dataset = parser.parse(xlsx, ImportType.TEACHERS, null);

        assertThat(dataset.detectedFormat()).isEqualTo(ImportSourceFormat.XLSX);
        assertThat(dataset.rows()).hasSize(2);
        assertThat(dataset.rows().get(0).get("employee_code")).isEqualTo("ENS-01");
        assertThat(dataset.rows().get(0).get("hourly_rate_xof")).isEqualTo("5000");
        assertThat(dataset.rows().get(1).get("teacher_type")).isEqualTo("TUTOR");
    }

    @Test
    void shouldRejectXxePayloadAndPathTraversalInXlsx() throws IOException {
        byte[] xxeXlsx = buildZip(
                "[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>",
                "xl/workbook.xml", "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"/>",
                "xl/worksheets/sheet1.xml",
                "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                        + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                        + "<sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>&xxe;</t></is></c></row></sheetData></worksheet>");

        assertThatThrownBy(() -> parser.parse(xxeXlsx, ImportType.STUDENTS, null))
                .isInstanceOf(SchoolImportValidationException.class)
                .hasMessageContaining("XML");

        byte[] traversalZip = buildZip(
                "../evil.xml", "<evil/>",
                "[Content_Types].xml", "<Types/>",
                "xl/workbook.xml", "<workbook/>");
        assertThatThrownBy(() -> parser.parse(traversalZip, ImportType.STUDENTS, null))
                .isInstanceOf(SchoolImportValidationException.class);
    }

    @Test
    void shouldRejectNonOoxmlZipAndExcessiveRowCount() throws IOException {
        byte[] plainZip = buildZip("hello.txt", "not an xlsx");
        assertThatThrownBy(() -> parser.parse(plainZip, ImportType.STUDENTS, null))
                .isInstanceOf(SchoolImportValidationException.class);

        StringBuilder tooManyRows = new StringBuilder("first_name;last_name;gender\n");
        for (int i = 0; i < 2005; i++) {
            tooManyRows.append("Prenom").append(i).append(";Nom").append(i).append(";MALE\n");
        }
        assertThatThrownBy(() -> parser.parse(
                tooManyRows.toString().getBytes(StandardCharsets.UTF_8), ImportType.STUDENTS, ';'))
                .isInstanceOf(SchoolImportValidationException.class);
    }

    public static byte[] buildMinimalXlsx(List<String> headers, List<List<String>> dataRows) throws IOException {
        List<String> sharedStrings = new java.util.ArrayList<>();
        StringBuilder sheet = new StringBuilder();
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");

        appendXlsxRow(sheet, 1, headers, sharedStrings);
        for (int i = 0; i < dataRows.size(); i++) {
            appendXlsxRow(sheet, i + 2, dataRows.get(i), sharedStrings);
        }
        sheet.append("</sheetData></worksheet>");

        StringBuilder sst = new StringBuilder();
        sst.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        for (String s : sharedStrings) {
            sst.append("<si><t>").append(escapeXml(s)).append("</t></si>");
        }
        sst.append("</sst>");

        return buildZip(
                "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>",
                "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"/>",
                "xl/sharedStrings.xml",
                sst.toString(),
                "xl/worksheets/sheet1.xml",
                sheet.toString());
    }

    private static void appendXlsxRow(
            StringBuilder sheet, int rowIndex, List<String> cells, List<String> sharedStrings) {
        sheet.append("<row r=\"").append(rowIndex).append("\">");
        for (int c = 0; c < cells.size(); c++) {
            String colRef = columnLetter(c) + rowIndex;
            String val = cells.get(c);
            if (val != null && val.matches("^[0-9]+$")) {
                sheet.append("<c r=\"").append(colRef).append("\"><v>").append(val).append("</v></c>");
            } else {
                int idx = sharedStrings.size();
                sharedStrings.add(val != null ? val : "");
                sheet.append("<c r=\"").append(colRef).append("\" t=\"s\"><v>").append(idx).append("</v></c>");
            }
        }
        sheet.append("</row>");
    }

    private static String columnLetter(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        do {
            sb.insert(0, (char) ('A' + (n % 26)));
            n = (n / 26) - 1;
        } while (n >= 0);
        return sb.toString();
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static byte[] buildZip(String... nameAndContentPairs) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (int i = 0; i < nameAndContentPairs.length; i += 2) {
                zos.putNextEntry(new ZipEntry(nameAndContentPairs[i]));
                zos.write(nameAndContentPairs[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
