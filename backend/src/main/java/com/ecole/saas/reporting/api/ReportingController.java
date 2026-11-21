package com.ecole.saas.reporting.api;

import com.ecole.saas.reporting.ReportFormat;
import com.ecole.saas.reporting.ReportType;
import com.ecole.saas.reporting.application.ReportExportService;
import com.ecole.saas.reporting.application.ReportingModels.AcademicReportView;
import com.ecole.saas.reporting.application.ReportingModels.AttendanceReportView;
import com.ecole.saas.reporting.application.ReportingModels.DownloadableReport;
import com.ecole.saas.reporting.application.ReportingModels.ExecutiveReportView;
import com.ecole.saas.reporting.application.ReportingModels.FinancialReportView;
import com.ecole.saas.reporting.application.ReportingModels.OperationsReportView;
import com.ecole.saas.reporting.application.ReportingModels.StudentOverviewReportView;
import com.ecole.saas.reporting.application.ReportingQueryService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST des rapports consolidés temps réel et des exports sécurisés JSON / CSV.
 */
@RestController
@RequestMapping("/api/v1/reporting")
public class ReportingController {

    private final ReportingQueryService queryService;
    private final ReportExportService exportService;

    public ReportingController(
            ReportingQueryService queryService,
            ReportExportService exportService) {
        this.queryService = queryService;
        this.exportService = exportService;
    }

    @GetMapping("/executive")
    public ResponseEntity<ExecutiveReportView> getExecutiveReport(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID evaluationPeriodId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            JwtAuthenticationToken authentication) {
        UUID actorId = ReportingApiSupport.extractActorId(authentication);
        ExecutiveReportView view = queryService.getExecutiveReport(
                academicYearId, evaluationPeriodId, fromDate, toDate, actorId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(view);
    }

    @GetMapping("/academic")
    public ResponseEntity<AcademicReportView> getAcademicReport(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID evaluationPeriodId,
            @RequestParam(required = false) UUID classRoomId,
            JwtAuthenticationToken authentication) {
        UUID actorId = ReportingApiSupport.extractActorId(authentication);
        AcademicReportView view = queryService.getAcademicReport(
                academicYearId, evaluationPeriodId, classRoomId, actorId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(view);
    }

    @GetMapping("/attendance")
    public ResponseEntity<AttendanceReportView> getAttendanceReport(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID evaluationPeriodId,
            @RequestParam(required = false) UUID classRoomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            JwtAuthenticationToken authentication) {
        UUID actorId = ReportingApiSupport.extractActorId(authentication);
        AttendanceReportView view = queryService.getAttendanceReport(
                academicYearId, evaluationPeriodId, classRoomId, fromDate, toDate, actorId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(view);
    }

    @GetMapping("/financial")
    public ResponseEntity<FinancialReportView> getFinancialReport(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID classRoomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            JwtAuthenticationToken authentication) {
        UUID actorId = ReportingApiSupport.extractActorId(authentication);
        FinancialReportView view = queryService.getFinancialReport(
                academicYearId, classRoomId, fromDate, toDate, actorId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(view);
    }

    @GetMapping("/operations")
    public ResponseEntity<OperationsReportView> getOperationsReport(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            JwtAuthenticationToken authentication) {
        UUID actorId = ReportingApiSupport.extractActorId(authentication);
        OperationsReportView view = queryService.getOperationsReport(
                academicYearId, fromDate, toDate, actorId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(view);
    }

    @GetMapping("/students/{studentId}/overview")
    public ResponseEntity<StudentOverviewReportView> getStudentOverview(
            @PathVariable UUID studentId,
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID evaluationPeriodId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            JwtAuthenticationToken authentication) {
        UUID actorId = ReportingApiSupport.extractActorId(authentication);
        StudentOverviewReportView view = queryService.getStudentOverview(
                studentId, academicYearId, evaluationPeriodId, fromDate, toDate, actorId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(view);
    }

    @GetMapping("/exports")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam ReportType reportType,
            @RequestParam(defaultValue = "CSV") ReportFormat format,
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID evaluationPeriodId,
            @RequestParam(required = false) UUID classRoomId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            JwtAuthenticationToken authentication) {
        UUID actorId = ReportingApiSupport.extractActorId(authentication);
        DownloadableReport report = exportService.exportReport(
                reportType, format, academicYearId, evaluationPeriodId, classRoomId, studentId, fromDate, toDate, actorId);
        return buildDownloadResponse(report);
    }

    static ResponseEntity<byte[]> buildDownloadResponse(DownloadableReport report) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(report.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(report.contentType()))
                .contentLength(report.fileSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Report-Sha256", report.sha256Hex())
                .body(report.content());
    }
}
