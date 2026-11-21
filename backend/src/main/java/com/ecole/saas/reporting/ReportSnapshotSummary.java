package com.ecole.saas.reporting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Vue immuable publique d'un snapshot de rapport persisté.
 */
public record ReportSnapshotSummary(
        UUID id,
        String snapshotNumber,
        ReportType reportType,
        ReportFormat outputFormat,
        ReportSnapshotStatus status,
        ReportScopeType scopeType,
        UUID academicYearId,
        UUID evaluationPeriodId,
        UUID classRoomId,
        UUID studentId,
        LocalDate fromDate,
        LocalDate toDate,
        String title,
        String fileName,
        long fileSizeBytes,
        String contentSha256,
        UUID generatedBy,
        Instant generatedAt,
        Instant archivedAt) {
}
