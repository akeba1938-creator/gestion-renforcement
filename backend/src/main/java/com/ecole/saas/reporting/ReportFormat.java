package com.ecole.saas.reporting;

/**
 * Formats d'export et de persistance de snapshots pris en charge par le module {@code reporting}.
 */
public enum ReportFormat {
    JSON("application/json; charset=UTF-8", "json"),
    CSV("text/csv; charset=UTF-8", "csv");

    private final String contentType;
    private final String fileExtension;

    ReportFormat(String contentType, String fileExtension) {
        this.contentType = contentType;
        this.fileExtension = fileExtension;
    }

    public String contentType() {
        return contentType;
    }

    public String fileExtension() {
        return fileExtension;
    }
}
