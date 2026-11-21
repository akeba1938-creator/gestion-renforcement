package com.ecole.saas.reporting.api;

import com.ecole.saas.reporting.ReportFormat;
import com.ecole.saas.reporting.ReportType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record GenerateReportSnapshotRequest(
        @NotNull(message = "Le type de rapport (reportType) est obligatoire.")
        ReportType reportType,
        @NotNull(message = "Le format de sortie (outputFormat) est obligatoire.")
        ReportFormat outputFormat,
        UUID academicYearId,
        UUID evaluationPeriodId,
        UUID classRoomId,
        UUID studentId,
        LocalDate fromDate,
        LocalDate toDate,
        @Size(max = 200, message = "Le titre ne doit pas dépasser 200 caractères.")
        String title,
        @Size(max = 128, message = "La clé d'idempotence ne doit pas dépasser 128 caractères.")
        String idempotencyKey) {
}
