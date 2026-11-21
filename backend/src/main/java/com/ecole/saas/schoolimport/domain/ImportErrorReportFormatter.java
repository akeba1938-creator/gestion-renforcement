package com.ecole.saas.schoolimport.domain;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Formateur CSV UTF-8 (avec BOM Excel et neutralisation anti-CSV-injection) des rapports d'erreurs détaillés.
 */
public final class ImportErrorReportFormatter {

    private static final char DELIMITER = ';';

    private ImportErrorReportFormatter() {
    }

    public static byte[] formatCsv(List<ImportJobRow> errorRows) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("row_number;natural_key;row_status;planned_action;error_code;error_field;error_message\r\n");
        if (errorRows != null) {
            for (ImportJobRow row : errorRows) {
                sb.append(row.getRowNumber()).append(DELIMITER);
                sb.append(cell(row.getNaturalKey())).append(DELIMITER);
                sb.append(cell(row.getRowStatus().name())).append(DELIMITER);
                sb.append(cell(row.getPlannedAction().name())).append(DELIMITER);
                sb.append(cell(row.getErrorCode())).append(DELIMITER);
                sb.append(cell(row.getErrorField())).append(DELIMITER);
                sb.append(cell(row.getErrorMessage())).append("\r\n");
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String cell(String value) {
        return ImportFileSecurityPolicy.escapeCsvCell(value != null ? value : "", DELIMITER);
    }
}
