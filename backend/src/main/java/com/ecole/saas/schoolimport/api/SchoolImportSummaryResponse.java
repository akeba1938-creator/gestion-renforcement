package com.ecole.saas.schoolimport.api;

import com.ecole.saas.schoolimport.ImportType;
import com.ecole.saas.schoolimport.application.SchoolImportJobService;
import java.util.Map;

public record SchoolImportSummaryResponse(
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

    public static SchoolImportSummaryResponse from(SchoolImportJobService.SchoolImportSummary summary) {
        return new SchoolImportSummaryResponse(
                summary.totalJobs(),
                summary.previewReadyCount(),
                summary.validationFailedCount(),
                summary.completedCount(),
                summary.completedWithErrorsCount(),
                summary.failedCount(),
                summary.cancelledCount(),
                summary.totalRowsProcessed(),
                summary.totalCreatedRows(),
                summary.totalUpdatedRows(),
                summary.totalSkippedRows(),
                summary.totalErrorRows(),
                summary.totalStorageBytes(),
                summary.countsByType());
    }
}
