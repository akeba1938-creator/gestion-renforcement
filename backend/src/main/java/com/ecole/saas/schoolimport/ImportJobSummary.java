package com.ecole.saas.schoolimport;

import java.time.Instant;
import java.util.UUID;

/**
 * Synthèse publique immuable d'un lot d'import scolaire exposée aux modules aval.
 */
public record ImportJobSummary(
        UUID id,
        String jobNumber,
        ImportType importType,
        ImportSourceFormat sourceFormat,
        ImportValidationMode validationMode,
        DuplicateStrategy duplicateStrategy,
        ImportJobStatus status,
        String fileName,
        long fileSizeBytes,
        int totalRows,
        int validRows,
        int errorRows,
        int duplicateRows,
        int createdCount,
        int updatedCount,
        int skippedCount,
        UUID createdBy,
        Instant analyzedAt,
        Instant executedAt) {
}
