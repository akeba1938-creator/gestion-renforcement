package com.ecole.saas.schoolimport;

import java.util.Map;

/**
 * Vue consolidée immuable de l'activité d'imports massifs exposée en lecture seule aux modules aval
 * (ex. {@code reporting}).
 */
public record SchoolImportActivitySummary(
        long totalJobs,
        long previewReadyCount,
        long validationFailedCount,
        long completedCount,
        long completedWithErrorsCount,
        long failedCount,
        long cancelledCount,
        long totalRowsProcessed,
        long totalCreatedRows,
        long totalUpdatedRows,
        long totalSkippedRows,
        long totalErrorRows,
        long totalStorageBytes,
        Map<ImportType, Long> countsByType) {
}
