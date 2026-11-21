package com.ecole.saas.reporting.application;

import com.ecole.saas.attendance.StudentAttendanceSummary;
import com.ecole.saas.finance.PaymentMethodBreakdown;
import com.ecole.saas.finance.StudentFinancialSummary;
import com.ecole.saas.grade.StudentPeriodGradeSummary;
import com.ecole.saas.reporting.ReportFormat;
import com.ecole.saas.reporting.ReportType;
import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schoolimport.ImportType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Modèles immuables de vue applicative pour les rapports consolidés (zéro entité JPA, zéro flottant).
 */
public final class ReportingModels {

    private ReportingModels() {
    }

    public record AcademicClassMetrics(
            UUID classRoomId,
            String classRoomCode,
            String classRoomName,
            String gradeLevelCode,
            String gradeLevelName,
            int capacity,
            long activeEnrollmentCount,
            BigDecimal occupancyRatePercent,
            int reportCardCount,
            int rankedStudentCount,
            int passedStudentCount,
            BigDecimal passRatePercent,
            BigDecimal classAverage,
            BigDecimal minAverage,
            BigDecimal maxAverage,
            int draftReportCardCount,
            int validatedReportCardCount,
            int lockedReportCardCount,
            Map<String, Long> mentionCounts) {
    }

    public record AcademicReportView(
            String schoolCode,
            String schoolName,
            UUID academicYearId,
            String academicYearCode,
            UUID evaluationPeriodId,
            String evaluationPeriodCode,
            int gradingScale,
            int classCount,
            long totalActiveStudentsInSchool,
            long totalEnrolledInScope,
            long totalCapacityInScope,
            BigDecimal occupancyRatePercent,
            int reportCardCount,
            int rankedStudentCount,
            int passedStudentCount,
            BigDecimal passRatePercent,
            BigDecimal overallAverage,
            BigDecimal minAverage,
            BigDecimal maxAverage,
            int draftReportCardCount,
            int validatedReportCardCount,
            int lockedReportCardCount,
            Map<String, Long> mentionCounts,
            List<AcademicClassMetrics> classes,
            Instant generatedAt) {
    }

    public record AttendanceClassMetrics(
            UUID classRoomId,
            String classRoomCode,
            String classRoomName,
            long sessionCount,
            long totalRecords,
            long presentCount,
            long lateCount,
            long totalLateMinutes,
            long absentCount,
            long justifiedAbsenceCount,
            long unjustifiedAbsenceCount,
            long excusedCount,
            BigDecimal attendanceRatePercent) {
    }

    public record AttendanceReportView(
            String schoolCode,
            String schoolName,
            UUID academicYearId,
            String academicYearCode,
            UUID evaluationPeriodId,
            String evaluationPeriodCode,
            LocalDate fromDate,
            LocalDate toDate,
            long sessionCount,
            long totalRecords,
            long presentCount,
            long lateCount,
            long totalLateMinutes,
            long absentCount,
            long justifiedAbsenceCount,
            long unjustifiedAbsenceCount,
            long excusedCount,
            BigDecimal attendanceRatePercent,
            List<AttendanceClassMetrics> classes,
            Instant generatedAt) {
    }

    public record FinancialClassMetrics(
            UUID classRoomId,
            String classRoomCode,
            String classRoomName,
            long enrolledStudentCount,
            long totalGrossXof,
            long totalDiscountXof,
            long totalNetXof,
            long totalPaidXof,
            long totalOutstandingXof,
            BigDecimal collectionRatePercent,
            long overdueInstallmentCount) {
    }

    public record FinancialReportView(
            String schoolCode,
            String schoolName,
            String currency,
            UUID academicYearId,
            String academicYearCode,
            LocalDate fromDate,
            LocalDate toDate,
            long totalInvoiceCount,
            long draftInvoiceCount,
            long issuedInvoiceCount,
            long partiallyPaidInvoiceCount,
            long paidInvoiceCount,
            long cancelledInvoiceCount,
            long totalGrossXof,
            long totalDiscountXof,
            long totalNetXof,
            long totalInvoicePaidXof,
            long totalOutstandingXof,
            BigDecimal collectionRatePercent,
            long overdueInstallmentCount,
            long overdueAmountXof,
            long approvedDiscountCount,
            long approvedDiscountXof,
            long approvedScholarshipCount,
            long approvedScholarshipXof,
            long pendingAdjustmentCount,
            long pendingAdjustmentXof,
            long collectedPaymentsCount,
            long collectedPaymentsXof,
            long reversalsCount,
            long reversalsXof,
            long netCollectedXof,
            long ledgerNetBalanceXof,
            boolean ledgerConsistentWithInvoices,
            long cashClosingTotalCount,
            long cashClosingSubmittedCount,
            long cashClosingValidatedCount,
            long cashClosingExpectedXof,
            long cashClosingCountedXof,
            long cashClosingDiscrepancyXof,
            List<PaymentMethodBreakdown> paymentMethods,
            List<FinancialClassMetrics> classes,
            Instant generatedAt) {
    }

    public record OperationsReportView(
            String schoolCode,
            String schoolName,
            UUID academicYearId,
            String academicYearCode,
            LocalDate fromDate,
            LocalDate toDate,
            long totalDocuments,
            long issuedDocumentsCount,
            long revokedDocumentsCount,
            long expiredDocumentsCount,
            long duplicateDocumentsCount,
            long totalDocumentFeesXof,
            long documentsStorageBytes,
            Map<DocumentType, Long> documentsByType,
            long totalImportJobs,
            long previewReadyImportsCount,
            long validationFailedImportsCount,
            long completedImportsCount,
            long completedWithErrorsImportsCount,
            long failedImportsCount,
            long cancelledImportsCount,
            long totalImportedRowsProcessed,
            long totalImportedCreatedRows,
            long totalImportedUpdatedRows,
            long totalImportedSkippedRows,
            long totalImportedErrorRows,
            long importsStorageBytes,
            Map<ImportType, Long> importsByType,
            long reportingSnapshotsCount,
            long reportingStorageBytes,
            Instant generatedAt) {
    }

    public record ExecutiveReportView(
            String schoolCode,
            String schoolName,
            String currency,
            UUID academicYearId,
            String academicYearCode,
            UUID evaluationPeriodId,
            String evaluationPeriodCode,
            LocalDate fromDate,
            LocalDate toDate,
            AcademicReportView academic,
            AttendanceReportView attendance,
            FinancialReportView financial,
            OperationsReportView operations,
            Instant generatedAt) {
    }

    public record StudentOverviewReportView(
            String schoolCode,
            String schoolName,
            UUID studentId,
            String studentMatricule,
            String studentFirstName,
            String studentLastName,
            String studentStatus,
            UUID academicYearId,
            String academicYearCode,
            UUID classRoomId,
            String classRoomCode,
            String classRoomName,
            List<StudentPeriodGradeSummary> gradeSummaries,
            StudentAttendanceSummary attendanceSummary,
            StudentFinancialSummary financialSummary,
            long issuedDocumentsCount,
            Instant generatedAt) {
    }

    public record DownloadableReport(
            ReportType reportType,
            ReportFormat format,
            String fileName,
            String contentType,
            long fileSizeBytes,
            String sha256Hex,
            byte[] content) {
    }
}
