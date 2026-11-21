package com.ecole.saas.reporting.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.reporting.ReportFormat;
import com.ecole.saas.reporting.ReportType;
import com.ecole.saas.reporting.application.ReportingAccessScopeGuard.CallerReportingScope;
import com.ecole.saas.reporting.application.ReportingModels.DownloadableReport;
import com.ecole.saas.reporting.domain.ReportSecurityPolicy;
import com.ecole.saas.reporting.domain.ReportingMetricsCalculator;
import com.ecole.saas.reporting.domain.ReportingValidationException;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.shared.domain.TenantContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service applicatif d'export direct sécurisé (JSON / CSV anti-CSV-Injection) avec contrôle RBAC,
 * filtrage fin par périmètre, contrôle d'abonnement en lecture et journalisation d'audit.
 */
@Service
public class ReportExportService {

    private final ReportingQueryService queryService;
    private final ReportPayloadSerializer serializer;
    private final ReportingSubscriptionGuard subscriptionGuard;
    private final ReportingAccessScopeGuard scopeGuard;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public ReportExportService(
            ReportingQueryService queryService,
            ReportPayloadSerializer serializer,
            ReportingSubscriptionGuard subscriptionGuard,
            ReportingAccessScopeGuard scopeGuard,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.queryService = queryService;
        this.serializer = serializer;
        this.subscriptionGuard = subscriptionGuard;
        this.scopeGuard = scopeGuard;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @PreAuthorize(ReportingRoles.ANY_REPORTING_ROLE)
    @Transactional
    public DownloadableReport exportReport(
            ReportType reportType,
            ReportFormat format,
            UUID academicYearId,
            UUID evaluationPeriodId,
            UUID classRoomId,
            UUID studentId,
            LocalDate fromDate,
            LocalDate toDate,
            UUID actorUserId) {
        TenantContext.requireCurrent();
        subscriptionGuard.assertReadAllowed();
        if (reportType == null) {
            throw new ReportingValidationException("Le type de rapport (reportType) est obligatoire.");
        }
        ReportFormat resolvedFormat = format != null ? format : ReportFormat.CSV;
        ReportingMetricsCalculator.validateDateRange(fromDate, toDate);

        CallerReportingScope scope = scopeGuard.resolveScope(actorUserId);
        scopeGuard.assertCanAccessReportType(scope, reportType);

        Object payload = switch (reportType) {
            case EXECUTIVE -> queryService.buildExecutiveReport(
                    academicYearId, evaluationPeriodId, fromDate, toDate, scope);
            case ACADEMIC -> queryService.buildAcademicReport(
                    academicYearId, evaluationPeriodId, classRoomId, scope);
            case ATTENDANCE -> queryService.buildAttendanceReport(
                    academicYearId, evaluationPeriodId, classRoomId, fromDate, toDate, scope);
            case FINANCIAL -> queryService.buildFinancialReport(
                    academicYearId, classRoomId, fromDate, toDate);
            case OPERATIONS -> queryService.buildOperationsReport(
                    academicYearId, fromDate, toDate);
            case STUDENT_OVERVIEW -> queryService.buildStudentOverview(
                    studentId, academicYearId, evaluationPeriodId, fromDate, toDate, scope);
        };

        byte[] content = serializer.serialize(reportType, resolvedFormat, payload);
        ReportSecurityPolicy.assertValidPayload(content);
        String sha256 = ReportSecurityPolicy.sha256Hex(content);

        SchoolProfileSnapshot school = subscriptionGuard.requireSchoolProfile();
        ZoneId zone = subscriptionGuard.resolveSchoolZone(school);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        String baseName = "export_" + school.code().toLowerCase(Locale.ROOT)
                + "_" + reportType.name().toLowerCase(Locale.ROOT)
                + "_" + today;
        String fileName = ReportSecurityPolicy.buildSafeFileName(baseName, resolvedFormat);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reportType", reportType.name());
        metadata.put("format", resolvedFormat.name());
        metadata.put("fileSizeBytes", content.length);
        metadata.put("sha256", sha256);
        if (academicYearId != null) {
            metadata.put("academicYearId", academicYearId.toString());
        }
        if (evaluationPeriodId != null) {
            metadata.put("evaluationPeriodId", evaluationPeriodId.toString());
        }
        if (classRoomId != null) {
            metadata.put("classRoomId", classRoomId.toString());
        }
        if (studentId != null) {
            metadata.put("studentId", studentId.toString());
        }

        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.REPORTING,
                AuditAction.REPORTING_EXPORT_DOWNLOADED,
                "REPORT_EXPORT",
                null,
                "Téléchargement d'un export consolidé (" + reportType + " / " + resolvedFormat + ").",
                metadata));

        return new DownloadableReport(
                reportType,
                resolvedFormat,
                fileName,
                resolvedFormat.contentType(),
                content.length,
                sha256,
                content);
    }
}
