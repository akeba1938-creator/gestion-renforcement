package com.ecole.saas.reporting.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.reporting.ReportFormat;
import com.ecole.saas.reporting.ReportScopeType;
import com.ecole.saas.reporting.ReportSnapshotEventType;
import com.ecole.saas.reporting.ReportSnapshotStatus;
import com.ecole.saas.reporting.ReportType;
import com.ecole.saas.reporting.application.ReportingAccessScopeGuard.CallerReportingScope;
import com.ecole.saas.reporting.application.ReportingAccessScopeGuard.ScopeMode;
import com.ecole.saas.reporting.application.ReportingModels.DownloadableReport;
import com.ecole.saas.reporting.application.ReportingQueryService.ResolvedAcademicContext;
import com.ecole.saas.reporting.domain.ReportSecurityPolicy;
import com.ecole.saas.reporting.domain.ReportSnapshot;
import com.ecole.saas.reporting.domain.ReportSnapshotBinaryStorage;
import com.ecole.saas.reporting.domain.ReportSnapshotEvent;
import com.ecole.saas.reporting.domain.ReportingConflictException;
import com.ecole.saas.reporting.domain.ReportingMetricsCalculator;
import com.ecole.saas.reporting.domain.ReportingNotFoundException;
import com.ecole.saas.reporting.domain.ReportingValidationException;
import com.ecole.saas.reporting.infrastructure.ReportSequenceRepository;
import com.ecole.saas.reporting.infrastructure.ReportSnapshotEventRepository;
import com.ecole.saas.reporting.infrastructure.ReportSnapshotRepository;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Service applicatif gérant le cycle de vie complet des snapshots de rapports consolidés :
 * génération idempotente numérotée, contrôle de quota {@code STORAGE_MB}, stockage binaire privé
 * immuable SHA-256, consultation filtrée par RBAC/périmètre, téléchargement vérifié, historique
 * immuable et archivage logique verrouillé ({@code PESSIMISTIC_WRITE}).
 */
@Service
public class ReportSnapshotService {

    public record GenerateSnapshotCommand(
            ReportType reportType,
            ReportFormat outputFormat,
            UUID academicYearId,
            UUID evaluationPeriodId,
            UUID classRoomId,
            UUID studentId,
            LocalDate fromDate,
            LocalDate toDate,
            String title,
            String idempotencyKey) {
    }

    public record SnapshotResult(
            ReportSnapshot snapshot,
            boolean idempotentReplay) {
    }

    private final ReportSnapshotRepository snapshotRepository;
    private final ReportSnapshotEventRepository eventRepository;
    private final ReportSequenceRepository sequenceRepository;
    private final ReportSnapshotBinaryStorage binaryStorage;
    private final ReportingQueryService queryService;
    private final ReportPayloadSerializer serializer;
    private final ReportingSubscriptionGuard subscriptionGuard;
    private final ReportingAccessScopeGuard scopeGuard;
    private final AuditLogPort auditLogPort;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final TransactionTemplate writeTransactionTemplate;
    private final Clock clock;

    public ReportSnapshotService(
            ReportSnapshotRepository snapshotRepository,
            ReportSnapshotEventRepository eventRepository,
            ReportSequenceRepository sequenceRepository,
            ReportSnapshotBinaryStorage binaryStorage,
            ReportingQueryService queryService,
            ReportPayloadSerializer serializer,
            ReportingSubscriptionGuard subscriptionGuard,
            ReportingAccessScopeGuard scopeGuard,
            AuditLogPort auditLogPort,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.eventRepository = eventRepository;
        this.sequenceRepository = sequenceRepository;
        this.binaryStorage = binaryStorage;
        this.queryService = queryService;
        this.serializer = serializer;
        this.subscriptionGuard = subscriptionGuard;
        this.scopeGuard = scopeGuard;
        this.auditLogPort = auditLogPort;
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
        this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @PreAuthorize(ReportingRoles.ANY_REPORTING_ROLE)
    public SnapshotResult generateSnapshot(GenerateSnapshotCommand command, UUID actorUserId) {
        UUID tenantId = TenantContext.requireCurrent().value();
        if (command == null || command.reportType() == null || command.outputFormat() == null) {
            throw new ReportingValidationException("Le type de rapport et le format de sortie sont obligatoires.");
        }
        ReportingMetricsCalculator.validateDateRange(command.fromDate(), command.toDate());
        String normalizedKey = ReportSecurityPolicy.normalizeIdempotencyKey(command.idempotencyKey());
        String requestHash = ReportSecurityPolicy.computeRequestHash(
                command.reportType(),
                command.outputFormat(),
                command.academicYearId(),
                command.evaluationPeriodId(),
                command.classRoomId(),
                command.studentId(),
                command.fromDate(),
                command.toDate(),
                command.title(),
                actorUserId);

        SnapshotPreparation preparation = readOnlyTransactionTemplate.execute(status -> {
            subscriptionGuard.assertWriteAllowed();
            CallerReportingScope scope = scopeGuard.resolveScope(actorUserId);
            scopeGuard.assertCanAccessReportType(scope, command.reportType());
            if (command.classRoomId() != null) {
                scopeGuard.assertCanAccessClassRoom(scope, command.classRoomId());
            }
            if (command.reportType() == ReportType.STUDENT_OVERVIEW) {
                if (command.studentId() == null) {
                    throw new ReportingValidationException(
                            "L'identifiant d'élève (studentId) est obligatoire pour un rapport STUDENT_OVERVIEW.");
                }
                scopeGuard.assertCanAccessStudent(scope, command.studentId());
            }

            Optional<ReportSnapshot> existingOpt = snapshotRepository.findByIdempotencyKey(normalizedKey);
            if (existingOpt.isPresent()) {
                ReportSnapshot existing = existingOpt.get();
                if (!existing.matchesRequestHash(requestHash)) {
                    throw new ReportingConflictException(
                            "reporting-idempotency-conflict",
                            "Cette clé d'idempotence a déjà été utilisée pour un snapshot avec des paramètres différents.");
                }
                scopeGuard.assertCanAccessSnapshot(scope, actorUserId, existing);
                return SnapshotPreparation.replay(existing);
            }

            SchoolProfileSnapshot school = subscriptionGuard.requireSchoolProfile();
            ResolvedAcademicContext academicCtx =
                    queryService.resolveAcademicContext(command.academicYearId(), command.evaluationPeriodId());
            UUID resolvedYearId = academicCtx.year() != null ? academicCtx.year().id() : command.academicYearId();
            UUID resolvedPeriodId = academicCtx.period() != null ? academicCtx.period().id() : command.evaluationPeriodId();

            Object payload = switch (command.reportType()) {
                case EXECUTIVE -> queryService.buildExecutiveReport(
                        resolvedYearId, resolvedPeriodId, command.fromDate(), command.toDate(), scope);
                case ACADEMIC -> queryService.buildAcademicReport(
                        resolvedYearId, resolvedPeriodId, command.classRoomId(), scope);
                case ATTENDANCE -> queryService.buildAttendanceReport(
                        resolvedYearId, resolvedPeriodId, command.classRoomId(), command.fromDate(), command.toDate(), scope);
                case FINANCIAL -> queryService.buildFinancialReport(
                        resolvedYearId, command.classRoomId(), command.fromDate(), command.toDate());
                case OPERATIONS -> queryService.buildOperationsReport(
                        resolvedYearId, command.fromDate(), command.toDate());
                case STUDENT_OVERVIEW -> queryService.buildStudentOverview(
                        command.studentId(), resolvedYearId, resolvedPeriodId, command.fromDate(), command.toDate(), scope);
            };
            return SnapshotPreparation.computed(scope, school, academicCtx, resolvedYearId, resolvedPeriodId, payload);
        });

        if (preparation.replayedSnapshot() != null) {
            return new SnapshotResult(preparation.replayedSnapshot(), true);
        }

        byte[] content = serializer.serialize(command.reportType(), command.outputFormat(), preparation.payload());
        ReportSecurityPolicy.assertValidPayload(content);
        String contentSha256 = ReportSecurityPolicy.sha256Hex(content);
        String summaryJson = serializer.buildSummaryJson(command.reportType(), preparation.payload());

        return writeTransactionTemplate.execute(status -> {
            subscriptionGuard.assertWriteAllowed();
            Optional<ReportSnapshot> existingOpt = snapshotRepository.findByIdempotencyKey(normalizedKey);
            if (existingOpt.isPresent()) {
                ReportSnapshot existing = existingOpt.get();
                if (!existing.matchesRequestHash(requestHash)) {
                    throw new ReportingConflictException(
                            "reporting-idempotency-conflict",
                            "Cette clé d'idempotence a déjà été utilisée pour un snapshot avec des paramètres différents.");
                }
                scopeGuard.assertCanAccessSnapshot(preparation.scope(), actorUserId, existing);
                return new SnapshotResult(existing, true);
            }

            long currentReportingBytes = snapshotRepository.sumFileSizeBytes();
            subscriptionGuard.assertStorageQuotaAvailableForNewSnapshot(currentReportingBytes, content.length);

            SchoolProfileSnapshot school = preparation.school();
            Instant now = clock.instant();
            ZoneId zone = subscriptionGuard.resolveSchoolZone(school);
            int sequenceYear = LocalDate.now(clock.withZone(zone)).getYear();
            int seqVal = sequenceRepository.nextSequenceValue(sequenceYear);
            String snapshotNumber = ReportSecurityPolicy.formatSnapshotNumber(school.code(), sequenceYear, seqVal);
            String fileName = ReportSecurityPolicy.buildSafeFileName(snapshotNumber, command.outputFormat());
            String storageKey = ReportSecurityPolicy.buildTenantScopedStorageKey(
                    tenantId, sequenceYear, snapshotNumber, command.outputFormat());

            ReportScopeType scopeType = resolveScopeType(command, preparation.scope());
            String resolvedTitle = (command.title() != null && !command.title().isBlank())
                    ? command.title().trim()
                    : defaultTitle(command.reportType(), school.code(), preparation.academicCtx());

            ReportSnapshot snapshot = new ReportSnapshot(
                    snapshotNumber,
                    normalizedKey,
                    requestHash,
                    command.reportType(),
                    command.outputFormat(),
                    scopeType,
                    preparation.resolvedYearId(),
                    preparation.resolvedPeriodId(),
                    command.classRoomId(),
                    command.studentId(),
                    command.fromDate(),
                    command.toDate(),
                    resolvedTitle,
                    fileName,
                    command.outputFormat().contentType(),
                    storageKey,
                    content.length,
                    contentSha256,
                    summaryJson,
                    actorUserId,
                    now);
            ReportSnapshot saved = snapshotRepository.save(snapshot);

            binaryStorage.store(
                    saved.getId(),
                    storageKey,
                    saved.getContentType(),
                    saved.getOutputFormat(),
                    contentSha256,
                    content);

            eventRepository.save(new ReportSnapshotEvent(
                    saved.getId(),
                    1,
                    ReportSnapshotEventType.GENERATED,
                    actorUserId,
                    "Snapshot de rapport généré (" + saved.getReportType() + " / " + saved.getOutputFormat() + ").",
                    now));

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("snapshotNumber", saved.getSnapshotNumber());
            metadata.put("reportType", saved.getReportType().name());
            metadata.put("outputFormat", saved.getOutputFormat().name());
            metadata.put("scopeType", saved.getScopeType().name());
            metadata.put("fileSizeBytes", saved.getFileSizeBytes());
            metadata.put("contentSha256", saved.getContentSha256());
            auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                    AuditCategory.REPORTING,
                    AuditAction.REPORTING_SNAPSHOT_GENERATED,
                    "REPORT_SNAPSHOT",
                    saved.getId(),
                    "Génération persistée d'un snapshot de rapport consolidé.",
                    metadata));

            return new SnapshotResult(saved, false);
        });
    }

    @PreAuthorize(ReportingRoles.ANY_REPORTING_ROLE)
    @Transactional(readOnly = true)
    public Page<ReportSnapshot> listSnapshots(
            ReportType reportType,
            ReportFormat outputFormat,
            ReportSnapshotStatus status,
            UUID academicYearId,
            UUID actorUserId,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.assertReadAllowed();
        CallerReportingScope scope = scopeGuard.resolveScope(actorUserId);
        if (reportType != null) {
            scopeGuard.assertCanAccessReportType(scope, reportType);
        }
        if (scope.allowedReportTypes().isEmpty()) {
            return Page.empty(pageable);
        }
        if ((scope.mode() == ScopeMode.PARENT_STUDENTS || scope.mode() == ScopeMode.SELF_STUDENT)
                && scope.studentIds().isEmpty()) {
            return Page.empty(pageable);
        }
        if (scope.mode() == ScopeMode.TEACHER_CLASSES
                && scope.classRoomIds().isEmpty()
                && scope.studentIds().isEmpty()) {
            return Page.empty(pageable);
        }

        Specification<ReportSnapshot> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("reportType").in(scope.allowedReportTypes()));
            if (scope.ownSnapshotsOnly()) {
                predicates.add(cb.equal(root.get("generatedBy"), actorUserId));
            }
            if (scope.mode() == ScopeMode.TEACHER_CLASSES) {
                List<Predicate> scopeBranches = new ArrayList<>();
                if (!scope.classRoomIds().isEmpty()) {
                    scopeBranches.add(root.get("classRoomId").in(scope.classRoomIds()));
                    scopeBranches.add(cb.and(
                            cb.isNull(root.get("classRoomId")),
                            cb.isNull(root.get("studentId"))));
                }
                if (!scope.studentIds().isEmpty()) {
                    scopeBranches.add(root.get("studentId").in(scope.studentIds()));
                }
                predicates.add(cb.or(scopeBranches.toArray(Predicate[]::new)));
            } else if (scope.mode() == ScopeMode.PARENT_STUDENTS || scope.mode() == ScopeMode.SELF_STUDENT) {
                predicates.add(root.get("studentId").in(scope.studentIds()));
            }
            if (reportType != null) {
                predicates.add(cb.equal(root.get("reportType"), reportType));
            }
            if (outputFormat != null) {
                predicates.add(cb.equal(root.get("outputFormat"), outputFormat));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (academicYearId != null) {
                predicates.add(cb.equal(root.get("academicYearId"), academicYearId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return snapshotRepository.findAll(spec, pageable);
    }

    @PreAuthorize(ReportingRoles.ANY_REPORTING_ROLE)
    @Transactional(readOnly = true)
    public ReportSnapshot getSnapshot(UUID snapshotId, UUID actorUserId) {
        TenantContext.requireCurrent();
        subscriptionGuard.assertReadAllowed();
        ReportSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ReportingNotFoundException(
                        "reporting-snapshot-not-found", "Le snapshot de rapport demandé est introuvable."));
        CallerReportingScope scope = scopeGuard.resolveScope(actorUserId);
        scopeGuard.assertCanAccessSnapshot(scope, actorUserId, snapshot);
        return snapshot;
    }

    @PreAuthorize(ReportingRoles.ANY_REPORTING_ROLE)
    @Transactional(readOnly = true)
    public List<ReportSnapshotEvent> listSnapshotEvents(UUID snapshotId, UUID actorUserId) {
        TenantContext.requireCurrent();
        subscriptionGuard.assertReadAllowed();
        ReportSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ReportingNotFoundException(
                        "reporting-snapshot-not-found", "Le snapshot de rapport demandé est introuvable."));
        CallerReportingScope scope = scopeGuard.resolveScope(actorUserId);
        scopeGuard.assertCanAccessSnapshot(scope, actorUserId, snapshot);
        return eventRepository.findBySnapshotIdOrderBySequenceNoAsc(snapshot.getId());
    }

    @PreAuthorize(ReportingRoles.ANY_REPORTING_ROLE)
    public DownloadableReport downloadSnapshot(UUID snapshotId, UUID actorUserId) {
        TenantContext.requireCurrent();
        SnapshotDownloadContext download = readOnlyTransactionTemplate.execute(status -> {
            subscriptionGuard.assertReadAllowed();
            ReportSnapshot snapshot = snapshotRepository.findById(snapshotId)
                    .orElseThrow(() -> new ReportingNotFoundException(
                            "reporting-snapshot-not-found", "Le snapshot de rapport demandé est introuvable."));
            CallerReportingScope scope = scopeGuard.resolveScope(actorUserId);
            scopeGuard.assertCanAccessSnapshot(scope, actorUserId, snapshot);

            byte[] content = binaryStorage.load(snapshot.getId(), snapshot.getContentSha256())
                    .orElseThrow(() -> new ReportingNotFoundException(
                            "reporting-snapshot-content-not-found",
                            "Le contenu binaire de ce snapshot est introuvable."));
            return new SnapshotDownloadContext(snapshot, content);
        });

        ReportSnapshot snapshot = download.snapshot();
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.REPORTING,
                AuditAction.REPORTING_SNAPSHOT_DOWNLOADED,
                "REPORT_SNAPSHOT",
                snapshot.getId(),
                "Téléchargement d'un snapshot de rapport consolidé.",
                Map.of(
                        "snapshotNumber", snapshot.getSnapshotNumber(),
                        "reportType", snapshot.getReportType().name(),
                        "outputFormat", snapshot.getOutputFormat().name(),
                        "fileSizeBytes", snapshot.getFileSizeBytes())));

        return new DownloadableReport(
                snapshot.getReportType(),
                snapshot.getOutputFormat(),
                snapshot.getFileName(),
                snapshot.getContentType(),
                snapshot.getFileSizeBytes(),
                snapshot.getContentSha256(),
                download.content());
    }

    @PreAuthorize(ReportingRoles.SNAPSHOT_ARCHIVE_ROLES)
    @Transactional
    public ReportSnapshot archiveSnapshot(UUID snapshotId, String reason, UUID actorUserId) {
        TenantContext.requireCurrent();
        subscriptionGuard.assertWriteAllowed();
        ReportSnapshot snapshot = snapshotRepository.findByIdForUpdate(snapshotId)
                .orElseThrow(() -> new ReportingNotFoundException(
                        "reporting-snapshot-not-found", "Le snapshot de rapport demandé est introuvable."));

        Instant now = clock.instant();
        snapshot.archive(actorUserId, now, reason);
        ReportSnapshot saved = snapshotRepository.save(snapshot);

        int nextSeq = eventRepository.findMaxSequenceNoBySnapshotId(saved.getId()) + 1;
        eventRepository.save(new ReportSnapshotEvent(
                saved.getId(),
                nextSeq,
                ReportSnapshotEventType.ARCHIVED,
                actorUserId,
                saved.getArchiveReason(),
                now));

        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.REPORTING,
                AuditAction.REPORTING_SNAPSHOT_ARCHIVED,
                "REPORT_SNAPSHOT",
                saved.getId(),
                "Archivage logique d'un snapshot de rapport consolidé.",
                Map.of(
                        "snapshotNumber", saved.getSnapshotNumber(),
                        "reportType", saved.getReportType().name())));

        return saved;
    }

    private static ReportScopeType resolveScopeType(GenerateSnapshotCommand command, CallerReportingScope scope) {
        if (command.reportType() == ReportType.STUDENT_OVERVIEW || command.studentId() != null) {
            return ReportScopeType.STUDENT;
        }
        if (command.classRoomId() != null || scope.mode() == ScopeMode.TEACHER_CLASSES) {
            return ReportScopeType.CLASSROOM;
        }
        return ReportScopeType.SCHOOL;
    }

    private static String defaultTitle(
            ReportType reportType, String schoolCode, ResolvedAcademicContext academicCtx) {
        String label = switch (reportType) {
            case EXECUTIVE -> "Rapport exécutif consolidé";
            case ACADEMIC -> "Rapport académique et résultats";
            case ATTENDANCE -> "Rapport d'assiduité et absences";
            case FINANCIAL -> "Rapport financier consolidé (FCFA)";
            case OPERATIONS -> "Rapport d'activité documents et imports";
            case STUDENT_OVERVIEW -> "Synthèse consolidée élève";
        };
        StringBuilder sb = new StringBuilder(label).append(" — ").append(schoolCode);
        if (academicCtx.year() != null) {
            sb.append(" (").append(academicCtx.year().code());
            if (academicCtx.period() != null) {
                sb.append(" / ").append(academicCtx.period().code());
            }
            sb.append(')');
        }
        String result = sb.toString();
        return result.length() > 200 ? result.substring(0, 200) : result;
    }

    private record SnapshotDownloadContext(ReportSnapshot snapshot, byte[] content) {
    }

    private record SnapshotPreparation(
            ReportSnapshot replayedSnapshot,
            CallerReportingScope scope,
            SchoolProfileSnapshot school,
            ResolvedAcademicContext academicCtx,
            UUID resolvedYearId,
            UUID resolvedPeriodId,
            Object payload) {

        static SnapshotPreparation replay(ReportSnapshot existing) {
            return new SnapshotPreparation(existing, null, null, null, null, null, null);
        }

        static SnapshotPreparation computed(
                CallerReportingScope scope,
                SchoolProfileSnapshot school,
                ResolvedAcademicContext academicCtx,
                UUID resolvedYearId,
                UUID resolvedPeriodId,
                Object payload) {
            return new SnapshotPreparation(null, scope, school, academicCtx, resolvedYearId, resolvedPeriodId, payload);
        }
    }
}
