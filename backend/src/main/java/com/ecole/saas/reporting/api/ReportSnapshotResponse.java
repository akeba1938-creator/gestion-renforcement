package com.ecole.saas.reporting.api;

import com.ecole.saas.reporting.ReportFormat;
import com.ecole.saas.reporting.ReportScopeType;
import com.ecole.saas.reporting.ReportSnapshotStatus;
import com.ecole.saas.reporting.ReportType;
import com.ecole.saas.reporting.domain.ReportSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReportSnapshotResponse(
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
        String contentType,
        long fileSizeBytes,
        String contentSha256,
        String summaryJson,
        UUID generatedBy,
        Instant generatedAt,
        Instant archivedAt,
        UUID archivedBy,
        String archiveReason) {

    static ReportSnapshotResponse from(ReportSnapshot s) {
        return new ReportSnapshotResponse(
                s.getId(),
                s.getSnapshotNumber(),
                s.getReportType(),
                s.getOutputFormat(),
                s.getStatus(),
                s.getScopeType(),
                s.getAcademicYearId(),
                s.getEvaluationPeriodId(),
                s.getClassRoomId(),
                s.getStudentId(),
                s.getFromDate(),
                s.getToDate(),
                s.getTitle(),
                s.getFileName(),
                s.getContentType(),
                s.getFileSizeBytes(),
                s.getContentSha256(),
                s.getSummaryJson(),
                s.getGeneratedBy(),
                s.getGeneratedAt(),
                s.getArchivedAt(),
                s.getArchivedBy(),
                s.getArchiveReason());
    }
}
