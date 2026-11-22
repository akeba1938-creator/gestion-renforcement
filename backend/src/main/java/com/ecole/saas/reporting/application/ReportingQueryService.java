package com.ecole.saas.reporting.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.AcademicYearSummary;
import com.ecole.saas.academic.ClassRoomSummary;
import com.ecole.saas.academic.EvaluationPeriodSummary;
import com.ecole.saas.academic.GradeLevelSummary;
import com.ecole.saas.attendance.AttendanceReportingOverview;
import com.ecole.saas.attendance.AttendanceSummaryPort;
import com.ecole.saas.attendance.ClassAttendanceReportingBreakdown;
import com.ecole.saas.attendance.StudentAttendanceSummary;
import com.ecole.saas.finance.FinanceAccountPort;
import com.ecole.saas.finance.FinancialReportingOverview;
import com.ecole.saas.finance.StudentFinancialSummary;
import com.ecole.saas.grade.ClassGradeReportingBreakdown;
import com.ecole.saas.grade.GradeReportingOverview;
import com.ecole.saas.grade.GradeSummaryPort;
import com.ecole.saas.grade.StudentPeriodGradeSummary;
import com.ecole.saas.reporting.ReportType;
import com.ecole.saas.reporting.application.ReportingAccessScopeGuard.CallerReportingScope;
import com.ecole.saas.reporting.application.ReportingAccessScopeGuard.ScopeMode;
import com.ecole.saas.reporting.application.ReportingModels.AcademicClassMetrics;
import com.ecole.saas.reporting.application.ReportingModels.AcademicReportView;
import com.ecole.saas.reporting.application.ReportingModels.AttendanceClassMetrics;
import com.ecole.saas.reporting.application.ReportingModels.AttendanceReportView;
import com.ecole.saas.reporting.application.ReportingModels.ExecutiveReportView;
import com.ecole.saas.reporting.application.ReportingModels.FinancialClassMetrics;
import com.ecole.saas.reporting.application.ReportingModels.FinancialReportView;
import com.ecole.saas.reporting.application.ReportingModels.OperationsReportView;
import com.ecole.saas.reporting.application.ReportingModels.StudentOverviewReportView;
import com.ecole.saas.reporting.domain.ReportingMetricsCalculator;
import com.ecole.saas.reporting.domain.ReportingNotFoundException;
import com.ecole.saas.reporting.domain.ReportingValidationException;
import com.ecole.saas.reporting.infrastructure.ReportSnapshotRepository;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.schooldocs.SchoolDocsActivitySummary;
import com.ecole.saas.schooldocs.SchoolDocsDirectoryPort;
import com.ecole.saas.schoolimport.SchoolImportActivitySummary;
import com.ecole.saas.schoolimport.SchoolImportDirectoryPort;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentEnrollmentSummary;
import com.ecole.saas.student.StudentSummary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service applicatif de calcul des rapports consolidés temps réel (exécutif, académique, assiduité,
 * financier FCFA, opérations documents/imports et synthèse individuelle élève) en lecture seule,
 * sans N+1 et avec contrôle RBAC + périmètre fin.
 */
@Service
public class ReportingQueryService {

    private final AcademicStructurePort academicStructurePort;
    private final StudentDirectoryPort studentDirectoryPort;
    private final GradeSummaryPort gradeSummaryPort;
    private final AttendanceSummaryPort attendanceSummaryPort;
    private final FinanceAccountPort financeAccountPort;
    private final SchoolDocsDirectoryPort schoolDocsDirectoryPort;
    private final SchoolImportDirectoryPort schoolImportDirectoryPort;
    private final ReportSnapshotRepository snapshotRepository;
    private final ReportingSubscriptionGuard subscriptionGuard;
    private final ReportingAccessScopeGuard scopeGuard;
    private final Clock clock;

    public ReportingQueryService(
            AcademicStructurePort academicStructurePort,
            StudentDirectoryPort studentDirectoryPort,
            GradeSummaryPort gradeSummaryPort,
            AttendanceSummaryPort attendanceSummaryPort,
            FinanceAccountPort financeAccountPort,
            SchoolDocsDirectoryPort schoolDocsDirectoryPort,
            SchoolImportDirectoryPort schoolImportDirectoryPort,
            ReportSnapshotRepository snapshotRepository,
            ReportingSubscriptionGuard subscriptionGuard,
            ReportingAccessScopeGuard scopeGuard,
            Clock clock) {
        this.academicStructurePort = academicStructurePort;
        this.studentDirectoryPort = studentDirectoryPort;
        this.gradeSummaryPort = gradeSummaryPort;
        this.attendanceSummaryPort = attendanceSummaryPort;
        this.financeAccountPort = financeAccountPort;
        this.schoolDocsDirectoryPort = schoolDocsDirectoryPort;
        this.schoolImportDirectoryPort = schoolImportDirectoryPort;
        this.snapshotRepository = snapshotRepository;
        this.subscriptionGuard = subscriptionGuard;
        this.scopeGuard = scopeGuard;
        this.clock = clock;
    }

    @PreAuthorize(ReportingRoles.EXECUTIVE_ROLES)
    @Transactional(readOnly = true)
    public ExecutiveReportView getExecutiveReport(
            UUID academicYearId,
            UUID evaluationPeriodId,
            LocalDate fromDate,
            LocalDate toDate,
            UUID actorUserId) {
        TenantContext.requireCurrent();
        subscriptionGuard.assertReadAllowed();
        CallerReportingScope scope = scopeGuard.resolveScope(actorUserId);
        scopeGuard.assertCanAccessReportType(scope, ReportType.EXECUTIVE);
        return buildExecutiveReport(academicYearId, evaluationPeriodId, fromDate, toDate, scope);
    }

    @PreAuthorize(ReportingRoles.ACADEMIC_ATTENDANCE_ROLES)
    @Transactional(readOnly = true)
    public AcademicReportView getAcademicReport(
            UUID academicYearId,
            UUID evaluationPeriodId,
            UUID classRoomId,
            UUID actorUserId) {
        TenantContext.requireCurrent();
        subscriptionGuard.assertReadAllowed();
        CallerReportingScope scope = scopeGuard.resolveScope(actorUserId);
        scopeGuard.assertCanAccessReportType(scope, ReportType.ACADEMIC);
        return buildAcademicReport(academicYearId, evaluationPeriodId, classRoomId, scope);
    }

    @PreAuthorize(ReportingRoles.ACADEMIC_ATTENDANCE_ROLES)
    @Transactional(readOnly = true)
    public AttendanceReportView getAttendanceReport(
            UUID academicYearId,
            UUID evaluationPeriodId,
            UUID classRoomId,
            LocalDate fromDate,
            LocalDate toDate,
            UUID actorUserId) {
        TenantContext.requireCurrent();
        subscriptionGuard.assertReadAllowed();
        CallerReportingScope scope = scopeGuard.resolveScope(actorUserId);
        scopeGuard.assertCanAccessReportType(scope, ReportType.ATTENDANCE);
        return buildAttendanceReport(academicYearId, evaluationPeriodId, classRoomId, fromDate, toDate, scope);
    }

    @PreAuthorize(ReportingRoles.FINANCIAL_ROLES)
    @Transactional(readOnly = true)
    public FinancialReportView getFinancialReport(
            UUID academicYearId,
            UUID classRoomId,
            LocalDate fromDate,
            LocalDate toDate,
            UUID actorUserId) {
        TenantContext.requireCurrent();
        subscriptionGuard.assertReadAllowed();
        CallerReportingScope scope = scopeGuard.resolveScope(actorUserId);
        scopeGuard.assertCanAccessReportType(scope, ReportType.FINANCIAL);
        return buildFinancialReport(academicYearId, classRoomId, fromDate, toDate);
    }

    @PreAuthorize(ReportingRoles.OPERATIONS_ROLES)
    @Transactional(readOnly = true)
    public OperationsReportView getOperationsReport(
            UUID academicYearId,
            LocalDate fromDate,
            LocalDate toDate,
            UUID actorUserId) {
        TenantContext.requireCurrent();
        subscriptionGuard.assertReadAllowed();
        CallerReportingScope scope = scopeGuard.resolveScope(actorUserId);
        scopeGuard.assertCanAccessReportType(scope, ReportType.OPERATIONS);
        return buildOperationsReport(academicYearId, fromDate, toDate);
    }

    @PreAuthorize(ReportingRoles.STUDENT_OVERVIEW_ROLES)
    @Transactional(readOnly = true)
    public StudentOverviewReportView getStudentOverview(
            UUID studentId,
            UUID academicYearId,
            UUID evaluationPeriodId,
            LocalDate fromDate,
            LocalDate toDate,
            UUID actorUserId) {
        TenantContext.requireCurrent();
        subscriptionGuard.assertReadAllowed();
        CallerReportingScope scope = scopeGuard.resolveScope(actorUserId);
        scopeGuard.assertCanAccessReportType(scope, ReportType.STUDENT_OVERVIEW);
        return buildStudentOverview(studentId, academicYearId, evaluationPeriodId, fromDate, toDate, scope);
    }

    ExecutiveReportView buildExecutiveReport(
            UUID academicYearId,
            UUID evaluationPeriodId,
            LocalDate fromDate,
            LocalDate toDate,
            CallerReportingScope scope) {
        ReportingMetricsCalculator.validateDateRange(fromDate, toDate);
        SchoolProfileSnapshot school = subscriptionGuard.requireSchoolProfile();
        ResolvedAcademicContext ctx = resolveAcademicContext(academicYearId, evaluationPeriodId);
        UUID resolvedYearId = ctx.year() != null ? ctx.year().id() : null;
        UUID resolvedPeriodId = ctx.period() != null ? ctx.period().id() : null;

        AcademicReportView academic = buildAcademicReport(resolvedYearId, resolvedPeriodId, null, scope);
        AttendanceReportView attendance =
                buildAttendanceReport(resolvedYearId, resolvedPeriodId, null, fromDate, toDate, scope);
        FinancialReportView financial = buildFinancialReport(resolvedYearId, null, fromDate, toDate);
        OperationsReportView operations = buildOperationsReport(resolvedYearId, fromDate, toDate);
        Instant now = clock.instant();

        return new ExecutiveReportView(
                school.code(),
                school.name(),
                school.currency(),
                resolvedYearId,
                ctx.year() != null ? ctx.year().code() : null,
                resolvedPeriodId,
                ctx.period() != null ? ctx.period().code() : null,
                fromDate,
                toDate,
                academic,
                attendance,
                financial,
                operations,
                now);
    }

    AcademicReportView buildAcademicReport(
            UUID academicYearId,
            UUID evaluationPeriodId,
            UUID classRoomId,
            CallerReportingScope scope) {
        SchoolProfileSnapshot school = subscriptionGuard.requireSchoolProfile();
        ResolvedAcademicContext ctx = resolveAcademicContext(academicYearId, evaluationPeriodId);
        scopeGuard.assertCanAccessClassRoom(scope, classRoomId);

        List<ClassRoomSummary> classes = resolveScopedClasses(ctx.year(), classRoomId, scope);
        List<UUID> classIds = classes.stream().map(ClassRoomSummary::id).toList();

        Map<UUID, GradeLevelSummary> levelsById = academicStructurePort.findGradeLevelsByIds(
                classes.stream().map(ClassRoomSummary::gradeLevelId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, Long> enrollmentCounts = studentDirectoryPort.countActiveEnrollmentsByClassRooms(classIds);

        UUID resolvedYearId = ctx.year() != null ? ctx.year().id() : null;
        UUID resolvedPeriodId = ctx.period() != null ? ctx.period().id() : null;

        GradeReportingOverview gradeOverview = classIds.isEmpty()
                ? emptyGradeOverview(resolvedYearId, resolvedPeriodId, school.gradingScale())
                : gradeSummaryPort.getPeriodReportingOverview(
                        resolvedYearId,
                        resolvedPeriodId,
                        (scope.mode() == ScopeMode.FULL_SCHOOL && classRoomId == null) ? null : classIds);

        Map<UUID, ClassGradeReportingBreakdown> gradeByClass = gradeOverview.classBreakdowns().stream()
                .collect(Collectors.toMap(ClassGradeReportingBreakdown::classRoomId, Function.identity(), (a, b) -> a));

        long totalEnrolledInScope = 0L;
        long totalCapacityInScope = 0L;
        List<AcademicClassMetrics> classMetrics = new ArrayList<>(classes.size());

        for (ClassRoomSummary room : classes) {
            long enrolled = enrollmentCounts.getOrDefault(room.id(), 0L);
            totalEnrolledInScope = Math.addExact(totalEnrolledInScope, enrolled);
            totalCapacityInScope = Math.addExact(totalCapacityInScope, room.capacity());
            GradeLevelSummary level = levelsById.get(room.gradeLevelId());
            ClassGradeReportingBreakdown gb = gradeByClass.get(room.id());

            classMetrics.add(new AcademicClassMetrics(
                    room.id(),
                    room.code(),
                    room.name(),
                    level != null ? level.code() : null,
                    level != null ? level.name() : null,
                    room.capacity(),
                    enrolled,
                    ReportingMetricsCalculator.percentage(enrolled, room.capacity()),
                    gb != null ? gb.reportCardCount() : 0,
                    gb != null ? gb.rankedStudentCount() : 0,
                    gb != null ? gb.passedStudentCount() : 0,
                    gb != null ? gb.passRatePercent() : null,
                    gb != null ? gb.classAverage() : null,
                    gb != null ? gb.minAverage() : null,
                    gb != null ? gb.maxAverage() : null,
                    gb != null ? gb.draftCount() : 0,
                    gb != null ? gb.validatedCount() : 0,
                    gb != null ? gb.lockedCount() : 0,
                    gb != null ? gb.mentionCounts() : Map.of()));
        }

        long totalActiveStudents = scope.mode() == ScopeMode.FULL_SCHOOL
                ? studentDirectoryPort.countActiveStudents()
                : totalEnrolledInScope;

        return new AcademicReportView(
                school.code(),
                school.name(),
                resolvedYearId,
                ctx.year() != null ? ctx.year().code() : null,
                resolvedPeriodId,
                ctx.period() != null ? ctx.period().code() : null,
                gradeOverview.reportCardCount() > 0 ? gradeOverview.gradingScale() : school.gradingScale(),
                classes.size(),
                totalActiveStudents,
                totalEnrolledInScope,
                totalCapacityInScope,
                ReportingMetricsCalculator.percentage(totalEnrolledInScope, totalCapacityInScope),
                gradeOverview.reportCardCount(),
                gradeOverview.rankedStudentCount(),
                gradeOverview.passedStudentCount(),
                gradeOverview.passRatePercent(),
                gradeOverview.overallAverage(),
                gradeOverview.minAverage(),
                gradeOverview.maxAverage(),
                gradeOverview.draftCount(),
                gradeOverview.validatedCount(),
                gradeOverview.lockedCount(),
                gradeOverview.mentionCounts(),
                List.copyOf(classMetrics),
                clock.instant());
    }

    AttendanceReportView buildAttendanceReport(
            UUID academicYearId,
            UUID evaluationPeriodId,
            UUID classRoomId,
            LocalDate fromDate,
            LocalDate toDate,
            CallerReportingScope scope) {
        ReportingMetricsCalculator.validateDateRange(fromDate, toDate);
        SchoolProfileSnapshot school = subscriptionGuard.requireSchoolProfile();
        ResolvedAcademicContext ctx = resolveAcademicContext(academicYearId, evaluationPeriodId);
        scopeGuard.assertCanAccessClassRoom(scope, classRoomId);

        List<ClassRoomSummary> classes = resolveScopedClasses(ctx.year(), classRoomId, scope);
        List<UUID> classIds = classes.stream().map(ClassRoomSummary::id).toList();
        UUID resolvedYearId = ctx.year() != null ? ctx.year().id() : null;
        UUID resolvedPeriodId = ctx.period() != null ? ctx.period().id() : null;

        AttendanceReportingOverview overview = classIds.isEmpty()
                ? new AttendanceReportingOverview(
                        resolvedYearId, resolvedPeriodId, fromDate, toDate,
                        0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null, List.of())
                : attendanceSummaryPort.getAttendanceReportingOverview(
                        resolvedYearId,
                        resolvedPeriodId,
                        (scope.mode() == ScopeMode.FULL_SCHOOL && classRoomId == null) ? null : classIds,
                        fromDate,
                        toDate);

        Map<UUID, ClassAttendanceReportingBreakdown> byClass = overview.classBreakdowns().stream()
                .collect(Collectors.toMap(
                        ClassAttendanceReportingBreakdown::classRoomId, Function.identity(), (a, b) -> a));

        List<AttendanceClassMetrics> classMetrics = new ArrayList<>(classes.size());
        for (ClassRoomSummary room : classes) {
            ClassAttendanceReportingBreakdown b = byClass.get(room.id());
            classMetrics.add(new AttendanceClassMetrics(
                    room.id(),
                    room.code(),
                    room.name(),
                    b != null ? b.sessionCount() : 0L,
                    b != null ? b.totalRecords() : 0L,
                    b != null ? b.presentCount() : 0L,
                    b != null ? b.lateCount() : 0L,
                    b != null ? b.totalLateMinutes() : 0L,
                    b != null ? b.absentCount() : 0L,
                    b != null ? b.justifiedAbsenceCount() : 0L,
                    b != null ? b.unjustifiedAbsenceCount() : 0L,
                    b != null ? b.excusedCount() : 0L,
                    b != null ? b.attendanceRatePercent() : null));
        }

        return new AttendanceReportView(
                school.code(),
                school.name(),
                resolvedYearId,
                ctx.year() != null ? ctx.year().code() : null,
                resolvedPeriodId,
                ctx.period() != null ? ctx.period().code() : null,
                fromDate,
                toDate,
                overview.sessionCount(),
                overview.totalRecords(),
                overview.presentCount(),
                overview.lateCount(),
                overview.totalLateMinutes(),
                overview.absentCount(),
                overview.justifiedAbsenceCount(),
                overview.unjustifiedAbsenceCount(),
                overview.excusedCount(),
                overview.attendanceRatePercent(),
                List.copyOf(classMetrics),
                clock.instant());
    }

    FinancialReportView buildFinancialReport(
            UUID academicYearId,
            UUID classRoomId,
            LocalDate fromDate,
            LocalDate toDate) {
        ReportingMetricsCalculator.validateDateRange(fromDate, toDate);
        SchoolProfileSnapshot school = subscriptionGuard.requireSchoolProfile();
        ResolvedAcademicContext ctx = resolveAcademicContext(academicYearId, null);
        UUID resolvedYearId = ctx.year() != null ? ctx.year().id() : null;

        List<ClassRoomSummary> classes = resolveScopedClasses(
                ctx.year(), classRoomId,
                new CallerReportingScope(ScopeMode.FULL_SCHOOL, Set.of(ReportType.FINANCIAL), Set.of(), Set.of(), true, false));

        FinancialReportingOverview overview =
                financeAccountPort.getFinancialReportingOverview(resolvedYearId, fromDate, toDate);

        List<FinancialClassMetrics> classMetrics = computeFinancialClassBreakdowns(classes, resolvedYearId);
        BigDecimal collectionRate = ReportingMetricsCalculator.collectionRatePercent(
                overview.totalInvoicePaidXof(), overview.totalNetXof());

        return new FinancialReportView(
                school.code(),
                school.name(),
                school.currency(),
                resolvedYearId,
                ctx.year() != null ? ctx.year().code() : null,
                fromDate,
                toDate,
                overview.totalInvoiceCount(),
                overview.draftInvoiceCount(),
                overview.issuedInvoiceCount(),
                overview.partiallyPaidInvoiceCount(),
                overview.paidInvoiceCount(),
                overview.cancelledInvoiceCount(),
                overview.totalGrossXof(),
                overview.totalDiscountXof(),
                overview.totalNetXof(),
                overview.totalInvoicePaidXof(),
                overview.totalOutstandingXof(),
                collectionRate,
                overview.overdueInstallmentCount(),
                overview.overdueAmountXof(),
                overview.approvedDiscountCount(),
                overview.approvedDiscountXof(),
                overview.approvedScholarshipCount(),
                overview.approvedScholarshipXof(),
                overview.pendingAdjustmentCount(),
                overview.pendingAdjustmentXof(),
                overview.collectedPaymentsCount(),
                overview.collectedPaymentsXof(),
                overview.reversalsCount(),
                overview.reversalsXof(),
                overview.netCollectedXof(),
                overview.ledgerNetBalanceXof(),
                overview.ledgerConsistentWithInvoices(),
                overview.cashClosingTotalCount(),
                overview.cashClosingSubmittedCount(),
                overview.cashClosingValidatedCount(),
                overview.cashClosingExpectedXof(),
                overview.cashClosingCountedXof(),
                overview.cashClosingDiscrepancyXof(),
                overview.paymentMethodBreakdowns(),
                classMetrics,
                clock.instant());
    }

    OperationsReportView buildOperationsReport(
            UUID academicYearId,
            LocalDate fromDate,
            LocalDate toDate) {
        ReportingMetricsCalculator.validateDateRange(fromDate, toDate);
        SchoolProfileSnapshot school = subscriptionGuard.requireSchoolProfile();
        ZoneId schoolZone = subscriptionGuard.resolveSchoolZone(school);
        ResolvedAcademicContext ctx = resolveAcademicContext(academicYearId, null);
        UUID resolvedYearId = ctx.year() != null ? ctx.year().id() : null;

        Instant startInclusive = fromDate != null ? fromDate.atStartOfDay(schoolZone).toInstant() : null;
        Instant endExclusive = toDate != null ? toDate.plusDays(1).atStartOfDay(schoolZone).toInstant() : null;

        SchoolDocsActivitySummary docs =
                schoolDocsDirectoryPort.getActivitySummary(resolvedYearId, startInclusive, endExclusive);
        SchoolImportActivitySummary imports =
                schoolImportDirectoryPort.getActivitySummary(resolvedYearId, startInclusive, endExclusive);

        long snapshotsCount = snapshotRepository.count();
        long reportingStorageBytes = snapshotRepository.sumFileSizeBytes();

        return new OperationsReportView(
                school.code(),
                school.name(),
                resolvedYearId,
                ctx.year() != null ? ctx.year().code() : null,
                fromDate,
                toDate,
                docs.totalDocuments(),
                docs.issuedCount(),
                docs.revokedCount(),
                docs.expiredCount(),
                docs.duplicateCount(),
                docs.totalFeeAmountXof(),
                docs.totalStorageBytes(),
                docs.countsByType(),
                imports.totalJobs(),
                imports.previewReadyCount(),
                imports.validationFailedCount(),
                imports.completedCount(),
                imports.completedWithErrorsCount(),
                imports.failedCount(),
                imports.cancelledCount(),
                imports.totalRowsProcessed(),
                imports.totalCreatedRows(),
                imports.totalUpdatedRows(),
                imports.totalSkippedRows(),
                imports.totalErrorRows(),
                imports.totalStorageBytes(),
                imports.countsByType(),
                snapshotsCount,
                reportingStorageBytes,
                clock.instant());
    }

    StudentOverviewReportView buildStudentOverview(
            UUID studentId,
            UUID academicYearId,
            UUID evaluationPeriodId,
            LocalDate fromDate,
            LocalDate toDate,
            CallerReportingScope scope) {
        if (studentId == null) {
            throw new ReportingValidationException("L'identifiant de l'élève est obligatoire.");
        }
        ReportingMetricsCalculator.validateDateRange(fromDate, toDate);
        scopeGuard.assertCanAccessStudent(scope, studentId);

        SchoolProfileSnapshot school = subscriptionGuard.requireSchoolProfile();
        StudentSummary student = studentDirectoryPort.findStudentById(studentId)
                .orElseThrow(() -> new ReportingNotFoundException(
                        "reporting-student-not-found", "L'élève demandé est introuvable."));

        ResolvedAcademicContext ctx = resolveAcademicContext(academicYearId, evaluationPeriodId);
        UUID resolvedYearId = ctx.year() != null ? ctx.year().id() : null;
        UUID resolvedPeriodId = ctx.period() != null ? ctx.period().id() : null;

        StudentEnrollmentSummary enrollment = resolvedYearId != null
                ? studentDirectoryPort.findEnrollmentByStudentAndYear(studentId, resolvedYearId).orElse(null)
                : studentDirectoryPort.findCurrentEnrollmentForStudent(studentId).orElse(null);

        ClassRoomSummary classRoom = enrollment != null
                ? academicStructurePort.findClassRoomById(enrollment.classRoomId()).orElse(null)
                : null;

        List<StudentPeriodGradeSummary> gradeSummaries;
        if (resolvedPeriodId != null) {
            gradeSummaries = gradeSummaryPort.findStudentPeriodSummary(studentId, resolvedPeriodId)
                    .map(List::of)
                    .orElse(List.of());
        } else if (resolvedYearId != null) {
            gradeSummaries = gradeSummaryPort.findStudentYearSummaries(studentId, resolvedYearId);
        } else {
            gradeSummaries = List.of();
        }

        StudentAttendanceSummary attendanceSummary = attendanceSummaryPort.getStudentSummary(
                studentId, resolvedYearId, resolvedPeriodId, fromDate, toDate);

        StudentFinancialSummary financialSummary = scope.canSeeStudentFinance()
                ? financeAccountPort.getStudentFinancialSummary(studentId, resolvedYearId)
                : null;

        long issuedDocumentsCount = schoolDocsDirectoryPort.countIssuedDocumentsByStudentId(studentId);

        return new StudentOverviewReportView(
                school.code(),
                school.name(),
                student.id(),
                student.matricule(),
                student.firstName(),
                student.lastName(),
                student.status(),
                resolvedYearId,
                ctx.year() != null ? ctx.year().code() : null,
                classRoom != null ? classRoom.id() : null,
                classRoom != null ? classRoom.code() : null,
                classRoom != null ? classRoom.name() : null,
                gradeSummaries,
                attendanceSummary,
                financialSummary,
                issuedDocumentsCount,
                clock.instant());
    }

    ResolvedAcademicContext resolveAcademicContext(UUID academicYearId, UUID evaluationPeriodId) {
        EvaluationPeriodSummary period = null;
        if (evaluationPeriodId != null) {
            period = academicStructurePort.findEvaluationPeriodById(evaluationPeriodId)
                    .orElseThrow(() -> new ReportingNotFoundException(
                            "reporting-period-not-found", "La période d'évaluation demandée est introuvable."));
        }
        AcademicYearSummary year;
        if (academicYearId != null) {
            year = academicStructurePort.findAcademicYearById(academicYearId)
                    .orElseThrow(() -> new ReportingNotFoundException(
                            "reporting-academic-year-not-found", "L'année scolaire demandée est introuvable."));
            if (period != null && !period.academicYearId().equals(year.id())) {
                throw new ReportingValidationException(
                        "reporting-period-year-mismatch",
                        "La période d'évaluation n'appartient pas à l'année scolaire indiquée.");
            }
        } else if (period != null) {
            year = academicStructurePort.findAcademicYearById(period.academicYearId()).orElse(null);
        } else {
            year = academicStructurePort.findCurrentAcademicYear().orElse(null);
        }
        return new ResolvedAcademicContext(year, period);
    }

    private List<ClassRoomSummary> resolveScopedClasses(
            AcademicYearSummary year,
            UUID classRoomId,
            CallerReportingScope scope) {
        if (classRoomId != null) {
            ClassRoomSummary room = academicStructurePort.findClassRoomById(classRoomId)
                    .orElseThrow(() -> new ReportingNotFoundException(
                            "reporting-classroom-not-found", "La classe demandée est introuvable."));
            if (year != null && !year.id().equals(room.academicYearId())) {
                throw new ReportingValidationException(
                        "reporting-classroom-year-mismatch",
                        "La classe demandée n'appartient pas à l'année scolaire indiquée.");
            }
            return List.of(room);
        }

        List<ClassRoomSummary> yearClasses = year != null
                ? academicStructurePort.findClassRoomsByAcademicYear(year.id())
                : List.of();

        if (scope.mode() == ScopeMode.TEACHER_CLASSES) {
            if (scope.classRoomIds().isEmpty()) {
                return List.of();
            }
            yearClasses = yearClasses.stream()
                    .filter(c -> scope.classRoomIds().contains(c.id()))
                    .toList();
        }
        return yearClasses.stream()
                .sorted(Comparator.comparing(ClassRoomSummary::code))
                .toList();
    }

    private List<FinancialClassMetrics> computeFinancialClassBreakdowns(
            List<ClassRoomSummary> classes, UUID academicYearId) {
        if (classes.isEmpty()) {
            return List.of();
        }
        List<UUID> classIds = classes.stream().map(ClassRoomSummary::id).toList();
        List<StudentEnrollmentSummary> enrollments = studentDirectoryPort.findActiveEnrollmentsByClassRooms(classIds);

        Map<UUID, List<UUID>> studentIdsByClass = new LinkedHashMap<>();
        for (ClassRoomSummary room : classes) {
            studentIdsByClass.put(room.id(), new ArrayList<>());
        }
        Set<UUID> allStudentIds = enrollments.stream()
                .map(StudentEnrollmentSummary::studentId)
                .collect(Collectors.toSet());
        for (StudentEnrollmentSummary enrollment : enrollments) {
            studentIdsByClass.computeIfAbsent(enrollment.classRoomId(), ignored -> new ArrayList<>())
                    .add(enrollment.studentId());
        }

        Map<UUID, StudentFinancialSummary> financialByStudent = new HashMap<>();
        if (!allStudentIds.isEmpty()) {
            for (StudentFinancialSummary summary :
                    financeAccountPort.getStudentFinancialSummariesByIds(allStudentIds, academicYearId)) {
                financialByStudent.put(summary.studentId(), summary);
            }
        }

        List<FinancialClassMetrics> result = new ArrayList<>(classes.size());
        for (ClassRoomSummary room : classes) {
            List<UUID> studentIds = studentIdsByClass.getOrDefault(room.id(), List.of());
            long gross = 0L;
            long discount = 0L;
            long net = 0L;
            long paid = 0L;
            long outstanding = 0L;
            long overdueCount = 0L;
            for (UUID sid : studentIds) {
                StudentFinancialSummary s = financialByStudent.get(sid);
                if (s != null) {
                    gross = ReportingMetricsCalculator.addXof(gross, s.totalGrossXof());
                    discount = ReportingMetricsCalculator.addXof(discount, s.totalDiscountXof());
                    net = ReportingMetricsCalculator.addXof(net, s.totalNetXof());
                    paid = ReportingMetricsCalculator.addXof(paid, s.totalPaidXof());
                    outstanding = ReportingMetricsCalculator.addXof(outstanding, s.totalBalanceXof());
                    overdueCount = Math.addExact(overdueCount, s.overdueInstallmentCount());
                }
            }
            result.add(new FinancialClassMetrics(
                    room.id(),
                    room.code(),
                    room.name(),
                    studentIds.size(),
                    gross,
                    discount,
                    net,
                    paid,
                    outstanding,
                    ReportingMetricsCalculator.collectionRatePercent(paid, net),
                    overdueCount));
        }
        return List.copyOf(result);
    }

    private static GradeReportingOverview emptyGradeOverview(
            UUID academicYearId, UUID evaluationPeriodId, int gradingScale) {
        return new GradeReportingOverview(
                academicYearId,
                evaluationPeriodId,
                gradingScale,
                0,
                0,
                0,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                Map.of(),
                List.of());
    }

    record ResolvedAcademicContext(AcademicYearSummary year, EvaluationPeriodSummary period) {
    }
}
