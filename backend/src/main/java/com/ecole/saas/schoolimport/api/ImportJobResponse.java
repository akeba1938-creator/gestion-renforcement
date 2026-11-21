package com.ecole.saas.schoolimport.api;

import com.ecole.saas.schoolimport.DuplicateStrategy;
import com.ecole.saas.schoolimport.ImportJobStatus;
import com.ecole.saas.schoolimport.ImportSourceFormat;
import com.ecole.saas.schoolimport.ImportType;
import com.ecole.saas.schoolimport.ImportValidationMode;
import com.ecole.saas.schoolimport.domain.ImportJob;
import java.time.Instant;
import java.util.UUID;

public record ImportJobResponse(
        UUID id,
        String jobNumber,
        ImportType importType,
        ImportSourceFormat sourceFormat,
        ImportValidationMode validationMode,
        DuplicateStrategy duplicateStrategy,
        ImportJobStatus status,
        UUID academicYearId,
        UUID evaluationId,
        UUID attendanceSessionId,
        String fileName,
        String contentType,
        long fileSizeBytes,
        String fileSha256,
        int totalRows,
        int validRows,
        int errorRows,
        int duplicateRows,
        int createdCount,
        int updatedCount,
        int skippedCount,
        String quotaResource,
        Long quotaLimit,
        Long quotaUsedBefore,
        Long quotaProjectedAfter,
        UUID createdBy,
        Instant analyzedAt,
        Instant executedAt,
        UUID executedBy,
        Instant cancelledAt,
        UUID cancelledBy,
        String cancellationReason,
        Instant createdAt) {

    public static ImportJobResponse from(ImportJob job) {
        return new ImportJobResponse(
                job.getId(),
                job.getJobNumber(),
                job.getImportType(),
                job.getSourceFormat(),
                job.getValidationMode(),
                job.getDuplicateStrategy(),
                job.getStatus(),
                job.getAcademicYearId(),
                job.getEvaluationId(),
                job.getAttendanceSessionId(),
                job.getFileName(),
                job.getContentType(),
                job.getFileSizeBytes(),
                job.getFileSha256(),
                job.getTotalRows(),
                job.getValidRows(),
                job.getErrorRows(),
                job.getDuplicateRows(),
                job.getCreatedCount(),
                job.getUpdatedCount(),
                job.getSkippedCount(),
                job.getQuotaResource(),
                job.getQuotaLimit(),
                job.getQuotaUsedBefore(),
                job.getQuotaProjectedAfter(),
                job.getCreatedBy(),
                job.getAnalyzedAt(),
                job.getExecutedAt(),
                job.getExecutedBy(),
                job.getCancelledAt(),
                job.getCancelledBy(),
                job.getCancellationReason(),
                job.getCreatedAt());
    }
}
