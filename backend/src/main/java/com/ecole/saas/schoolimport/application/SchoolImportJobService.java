package com.ecole.saas.schoolimport.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.schoolimport.DuplicateStrategy;
import com.ecole.saas.schoolimport.ImportJobEventType;
import com.ecole.saas.schoolimport.ImportJobStatus;
import com.ecole.saas.schoolimport.ImportRowAction;
import com.ecole.saas.schoolimport.ImportRowStatus;
import com.ecole.saas.schoolimport.ImportType;
import com.ecole.saas.schoolimport.ImportValidationMode;
import com.ecole.saas.schoolimport.application.SchoolImportRowAnalyzer.AnalysisResult;
import com.ecole.saas.schoolimport.application.SchoolImportRowAnalyzer.AnalyzedRow;
import com.ecole.saas.schoolimport.application.SchoolImportRowExecutor.BatchExecutionResult;
import com.ecole.saas.schoolimport.application.SchoolImportRowExecutor.RowExecutionOutcome;
import com.ecole.saas.schoolimport.domain.ImportColumnCatalog;
import com.ecole.saas.schoolimport.domain.ImportErrorReportFormatter;
import com.ecole.saas.schoolimport.domain.ImportFileBinaryStorage;
import com.ecole.saas.schoolimport.domain.ImportFileSecurityPolicy;
import com.ecole.saas.schoolimport.domain.ImportJob;
import com.ecole.saas.schoolimport.domain.ImportJobEvent;
import com.ecole.saas.schoolimport.domain.ImportJobNumberFormatter;
import com.ecole.saas.schoolimport.domain.ImportJobRow;
import com.ecole.saas.schoolimport.domain.ParsedTabularDataset;
import com.ecole.saas.schoolimport.domain.SchoolImportConflictException;
import com.ecole.saas.schoolimport.domain.SchoolImportNotFoundException;
import com.ecole.saas.schoolimport.domain.SchoolImportValidationException;
import com.ecole.saas.schoolimport.domain.TabularFileParser;
import com.ecole.saas.schoolimport.infrastructure.ImportJobEventRepository;
import com.ecole.saas.schoolimport.infrastructure.ImportJobRepository;
import com.ecole.saas.schoolimport.infrastructure.ImportJobRowRepository;
import com.ecole.saas.schoolimport.infrastructure.ImportSequenceRepository;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Service applicatif orchestrant le cycle de vie complet des lots d'import massif scolaire :
 * téléversement sécurisé, analyse à sec / prévisualisation, validation stricte ligne par ligne,
 * exécution idempotente verrouillée en {@code PESSIMISTIC_WRITE}, annulation tracée,
 * téléchargement des modèles et rapports d'erreurs CSV, et synthèses SQL agrégées.
 */
@Service
public class SchoolImportJobService {

    public record CreateImportJobCommand(
            ImportType importType,
            ImportValidationMode validationMode,
            DuplicateStrategy duplicateStrategy,
            UUID academicYearId,
            UUID evaluationId,
            UUID attendanceSessionId,
            Boolean executeImmediately,
            Character delimiter,
            String originalFileName,
            byte[] fileBytes,
            String idempotencyKey) {
    }

    public record ImportJobResult(
            ImportJob job,
            boolean idempotentReplay) {
    }

    public record DownloadableCsv(
            String fileName,
            byte[] content) {
    }

    public record SchoolImportSummary(
            long totalJobs,
            long previewReadyCount,
            long validationFailedCount,
            long completedCount,
            long completedWithErrorsCount,
            long failedCount,
            long cancelledCount,
            long totalRowsProcessed,
            long totalCreatedRows,
            long totalUpdatedRows,
            long totalSkippedRows,
            long totalErrorRows,
            long totalStorageBytes,
            Map<ImportType, Long> countsByType) {
    }

    private final ImportJobRepository jobRepository;
    private final ImportJobRowRepository rowRepository;
    private final ImportJobEventRepository eventRepository;
    private final ImportSequenceRepository sequenceRepository;
    private final ImportFileBinaryStorage binaryStorage;
    private final TabularFileParser tabularFileParser;
    private final SchoolImportRowAnalyzer rowAnalyzer;
    private final SchoolImportRowExecutor rowExecutor;
    private final SchoolImportAccessGuard accessGuard;
    private final SchoolImportSubscriptionGuard subscriptionGuard;
    private final AuditLogPort auditLogPort;
    private final TransactionTemplate requiresNewTxTemplate;
    private final Clock clock;

    public SchoolImportJobService(
            ImportJobRepository jobRepository,
            ImportJobRowRepository rowRepository,
            ImportJobEventRepository eventRepository,
            ImportSequenceRepository sequenceRepository,
            ImportFileBinaryStorage binaryStorage,
            TabularFileParser tabularFileParser,
            SchoolImportRowAnalyzer rowAnalyzer,
            SchoolImportRowExecutor rowExecutor,
            SchoolImportAccessGuard accessGuard,
            SchoolImportSubscriptionGuard subscriptionGuard,
            AuditLogPort auditLogPort,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.eventRepository = eventRepository;
        this.sequenceRepository = sequenceRepository;
        this.binaryStorage = binaryStorage;
        this.tabularFileParser = tabularFileParser;
        this.rowAnalyzer = rowAnalyzer;
        this.rowExecutor = rowExecutor;
        this.accessGuard = accessGuard;
        this.subscriptionGuard = subscriptionGuard;
        this.auditLogPort = auditLogPort;
        this.requiresNewTxTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @PreAuthorize(SchoolImportRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public List<ImportColumnCatalog.TemplateDescriptor> listTemplates() {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        if (accessGuard.isRestrictedTeacher()) {
            return List.of(
                    ImportColumnCatalog.descriptorFor(ImportType.GRADES),
                    ImportColumnCatalog.descriptorFor(ImportType.ATTENDANCE));
        }
        return ImportColumnCatalog.listAll();
    }

    @PreAuthorize(SchoolImportRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public DownloadableCsv downloadTemplate(ImportType importType) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        if (importType == null) {
            throw new SchoolImportValidationException("Le type de modèle d'import est obligatoire.");
        }
        accessGuard.assertCanReadTemplate(importType);
        byte[] csvBytes = ImportColumnCatalog.buildCsvTemplate(importType);
        String fileName = ImportFileSecurityPolicy.buildSafeCsvDownloadName("modele-import", importType.name());
        return new DownloadableCsv(fileName, csvBytes);
    }

    @PreAuthorize(SchoolImportRoles.WRITE_ROLES)
    public ImportJobResult createAndAnalyzeJob(CreateImportJobCommand command, UUID actorId) {
        TenantId tenantId = TenantContext.requireCurrent();
        if (command == null || command.importType() == null) {
            throw new SchoolImportValidationException("Le type d'import est obligatoire.");
        }
        accessGuard.assertCanWriteImportType(command.importType());
        String idempotencyKey = requireIdempotencyKey(command.idempotencyKey(), "Idempotency-Key");

        ImportValidationMode validationMode = command.validationMode() != null
                ? command.validationMode()
                : ImportValidationMode.STRICT_ALL_OR_NOTHING;
        DuplicateStrategy duplicateStrategy = command.duplicateStrategy() != null
                ? command.duplicateStrategy()
                : DuplicateStrategy.FAIL_ON_DUPLICATE;
        boolean executeImmediately = Boolean.TRUE.equals(command.executeImmediately());

        // Validation sécurité réelle du fichier hors transaction longue
        byte[] rawBytes = command.fileBytes();
        ImportFileSecurityPolicy.detectAndValidateFormat(rawBytes);
        String fileSha256 = ImportFileSecurityPolicy.sha256Hex(rawBytes);
        String requestHash = computeCreateRequestHash(
                command.importType(),
                validationMode,
                duplicateStrategy,
                command.academicYearId(),
                command.evaluationId(),
                command.attendanceSessionId(),
                executeImmediately,
                fileSha256);

        ImportJobResult createdResult = TenantContext.executeWithTenant(
                tenantId,
                () -> requiresNewTxTemplate.execute(status -> {
                    SchoolProfileSnapshot school = subscriptionGuard.requireWriteAccess();

                    Optional<ImportJob> existingOpt = jobRepository.findByIdempotencyKey(idempotencyKey);
                    if (existingOpt.isPresent()) {
                        ImportJob existing = existingOpt.get();
                        if (!existing.getRequestHash().equals(requestHash)) {
                            throw new SchoolImportConflictException(
                                    "schoolimport-idempotency-conflict",
                                    "Cette clé d'idempotence a déjà été utilisée avec un fichier ou des paramètres différents.");
                        }
                        accessGuard.assertCanReadJob(existing, actorId);
                        return new ImportJobResult(existing, true);
                    }

                    subscriptionGuard.assertStorageQuotaAvailable(rawBytes.length);

                    ParsedTabularDataset dataset = tabularFileParser.parse(
                            rawBytes, command.importType(), command.delimiter());

                    Instant now = clock.instant();
                    ZoneId schoolZone = subscriptionGuard.resolveSchoolZone(school);
                    LocalDate schoolToday = now.atZone(schoolZone).toLocalDate();

                    AnalysisResult analysis = rowAnalyzer.analyze(
                            command.importType(),
                            duplicateStrategy,
                            dataset.rows(),
                            command.academicYearId(),
                            command.evaluationId(),
                            command.attendanceSessionId(),
                            actorId,
                            schoolToday);

                    ImportJobStatus initialStatus = determinePreviewStatus(validationMode, analysis);
                    int sequenceYear = schoolToday.getYear();
                    int nextSeq = sequenceRepository.nextSequenceValue(sequenceYear);
                    String jobNumber = ImportJobNumberFormatter.format(school.code(), sequenceYear, nextSeq);
                    String safeFileName = ImportFileSecurityPolicy.sanitizeFileName(
                            command.originalFileName(), dataset.detectedFormat());
                    String contentType = ImportFileSecurityPolicy.canonicalContentType(dataset.detectedFormat());
                    String storageKey = ImportFileSecurityPolicy.buildTenantScopedStorageKey(
                            tenantId.value(), sequenceYear, jobNumber, dataset.detectedFormat());

                    ImportJob savedJob = jobRepository.save(new ImportJob(
                            jobNumber,
                            idempotencyKey,
                            requestHash,
                            command.importType(),
                            dataset.detectedFormat(),
                            validationMode,
                            duplicateStrategy,
                            initialStatus,
                            analysis.resolvedAcademicYearId(),
                            analysis.resolvedEvaluationId(),
                            analysis.resolvedAttendanceSessionId(),
                            safeFileName,
                            contentType,
                            storageKey,
                            rawBytes.length,
                            fileSha256,
                            analysis.totalRows(),
                            analysis.validRows(),
                            analysis.errorRows(),
                            analysis.duplicateRows(),
                            analysis.quotaResource(),
                            analysis.quotaLimit(),
                            analysis.quotaUsedBefore(),
                            analysis.quotaProjectedAfter(),
                            actorId,
                            now));

                    binaryStorage.store(
                            savedJob.getId(),
                            storageKey,
                            contentType,
                            dataset.detectedFormat(),
                            fileSha256,
                            rawBytes);

                    List<ImportJobRow> rowEntities = analysis.rows().stream()
                            .map(r -> new ImportJobRow(
                                    savedJob.getId(),
                                    r.rowNumber(),
                                    r.naturalKey(),
                                    r.rowStatus(),
                                    r.plannedAction(),
                                    r.errorCode(),
                                    r.errorField(),
                                    r.errorMessage(),
                                    r.targetEntityType(),
                                    r.targetEntityId(),
                                    r.previewJson()))
                            .toList();
                    rowRepository.saveAll(rowEntities);

                    eventRepository.save(new ImportJobEvent(
                            savedJob.getId(),
                            1,
                            ImportJobEventType.UPLOADED,
                            actorId,
                            "Fichier reçu et vérifié (" + dataset.detectedFormat() + ", "
                                    + rawBytes.length + " octets).",
                            now));
                    eventRepository.save(new ImportJobEvent(
                            savedJob.getId(),
                            2,
                            ImportJobEventType.ANALYZED,
                            actorId,
                            "Analyse à sec terminée : " + analysis.totalRows() + " lignes, "
                                    + analysis.validRows() + " valides, " + analysis.duplicateRows()
                                    + " doublons, " + analysis.errorRows() + " erreurs.",
                            now));

                    auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                            AuditCategory.SCHOOLIMPORT,
                            AuditAction.SCHOOLIMPORT_JOB_CREATED,
                            "SCHOOLIMPORT_JOB",
                            savedJob.getId(),
                            "Création et analyse à sec d'un lot d'import massif.",
                            Map.of(
                                    "jobNumber", savedJob.getJobNumber(),
                                    "importType", savedJob.getImportType().name(),
                                    "sourceFormat", savedJob.getSourceFormat().name(),
                                    "status", savedJob.getStatus().name(),
                                    "totalRows", savedJob.getTotalRows(),
                                    "errorRows", savedJob.getErrorRows())));

                    return new ImportJobResult(savedJob, false);
                }));

        if (!createdResult.idempotentReplay()
                && executeImmediately
                && createdResult.job().getStatus().isExecutable()) {
            String autoExecKey = idempotencyKey.length() <= 120
                    ? idempotencyKey + ":exec"
                    : ImportFileSecurityPolicy.sha256Hex(idempotencyKey + ":exec");
            return executeJob(createdResult.job().getId(), autoExecKey, actorId);
        }
        return createdResult;
    }

    @PreAuthorize(SchoolImportRoles.WRITE_ROLES)
    public ImportJobResult executeJob(UUID jobId, String executionIdempotencyKey, UUID actorId) {
        TenantId tenantId = TenantContext.requireCurrent();
        String execKey = requireIdempotencyKey(executionIdempotencyKey, "Idempotency-Key");

        record PreparedExecution(
                ImportJob replayJob,
                ImportType importType,
                ImportValidationMode validationMode,
                DuplicateStrategy duplicateStrategy,
                UUID academicYearId,
                UUID evaluationId,
                UUID attendanceSessionId,
                String expectedSha256,
                byte[] fileBytes,
                LocalDate schoolToday) {
        }

        // Phase 1 : verrouillage PESSIMISTIC_WRITE + contrôle idempotence + passage en PROCESSING
        PreparedExecution prepared = TenantContext.executeWithTenant(
                tenantId,
                () -> requiresNewTxTemplate.execute(status -> {
                    SchoolProfileSnapshot school = subscriptionGuard.requireWriteAccess();

                    ImportJob job = jobRepository.findByIdForUpdate(jobId)
                            .orElseThrow(() -> new SchoolImportNotFoundException("Lot d'import introuvable."));
                    accessGuard.assertCanMutateJob(job, actorId);

                    if (execKey.equals(job.getExecutionIdempotencyKey()) && job.getStatus().isTerminal()) {
                        return new PreparedExecution(
                                job, null, null, null, null, null, null, null, null, null);
                    }
                    Optional<ImportJob> otherWithExecKey = jobRepository.findByExecutionIdempotencyKey(execKey);
                    if (otherWithExecKey.isPresent() && !otherWithExecKey.get().getId().equals(job.getId())) {
                        throw new SchoolImportConflictException(
                                "schoolimport-idempotency-conflict",
                                "Cette clé d'idempotence d'exécution a déjà été utilisée sur un autre lot.");
                    }

                    job.beginProcessing(execKey);
                    jobRepository.save(job);

                    int nextSeq = eventRepository.findMaxSequenceNoByJobId(job.getId()) + 1;
                    eventRepository.save(new ImportJobEvent(
                            job.getId(),
                            nextSeq,
                            ImportJobEventType.EXECUTION_STARTED,
                            actorId,
                            "Exécution contrôlée du lot d'import démarrée (" + job.getValidationMode() + ").",
                            clock.instant()));

                    byte[] bytes = binaryStorage.load(job.getId())
                            .orElseThrow(() -> new SchoolImportNotFoundException(
                                    "Contenu binaire du fichier source introuvable."));
                    String computedSha256 = ImportFileSecurityPolicy.sha256Hex(bytes);
                    if (!computedSha256.equalsIgnoreCase(job.getFileSha256())) {
                        throw new SchoolImportConflictException(
                                "schoolimport-file-integrity-mismatch",
                                "L'intégrité SHA-256 du fichier source n'est plus vérifiée.");
                    }

                    LocalDate schoolToday = clock.instant()
                            .atZone(subscriptionGuard.resolveSchoolZone(school))
                            .toLocalDate();

                    return new PreparedExecution(
                            null,
                            job.getImportType(),
                            job.getValidationMode(),
                            job.getDuplicateStrategy(),
                            job.getAcademicYearId(),
                            job.getEvaluationId(),
                            job.getAttendanceSessionId(),
                            job.getFileSha256(),
                            bytes,
                            schoolToday);
                }));

        if (prepared.replayJob() != null) {
            return new ImportJobResult(prepared.replayJob(), true);
        }

        // Phase 2 : ré-analyse fraîche contre l'état courant + exécution contrôlée
        AnalysisResult freshAnalysis = TenantContext.executeWithTenant(
                tenantId,
                () -> requiresNewTxTemplate.execute(status -> {
                    ParsedTabularDataset dataset = tabularFileParser.parse(
                            prepared.fileBytes(), prepared.importType(), null);
                    return rowAnalyzer.analyze(
                            prepared.importType(),
                            prepared.duplicateStrategy(),
                            dataset.rows(),
                            prepared.academicYearId(),
                            prepared.evaluationId(),
                            prepared.attendanceSessionId(),
                            actorId,
                            prepared.schoolToday());
                }));

        BatchExecutionResult execResult = rowExecutor.execute(
                tenantId,
                prepared.importType(),
                prepared.validationMode(),
                freshAnalysis,
                actorId);

        // Phase 3 : verrouillage PESSIMISTIC_WRITE + finalisation atomique du job, saveAll des lignes, event + audit
        ImportJob finalized = TenantContext.executeWithTenant(
                tenantId,
                () -> requiresNewTxTemplate.execute(status -> {
                    ImportJob job = jobRepository.findByIdForUpdate(jobId)
                            .orElseThrow(() -> new SchoolImportNotFoundException("Lot d'import introuvable."));

                    Map<Integer, ImportJobRow> existingRowsByNum = rowRepository
                            .findByJobIdOrderByRowNumberAsc(jobId).stream()
                            .collect(Collectors.toMap(ImportJobRow::getRowNumber, Function.identity()));

                    List<ImportJobRow> updatedRows = new ArrayList<>(existingRowsByNum.size());
                    for (Map.Entry<Integer, RowExecutionOutcome> entry : execResult.outcomesByRowNumber().entrySet()) {
                        ImportJobRow rowEntity = existingRowsByNum.get(entry.getKey());
                        if (rowEntity == null) {
                            continue;
                        }
                        RowExecutionOutcome outcome = entry.getValue();
                        switch (outcome.finalStatus()) {
                            case CREATED -> rowEntity.markCreated(
                                    outcome.entityType(), outcome.entityId(), outcome.updatedNaturalKey());
                            case UPDATED -> rowEntity.markUpdated(
                                    outcome.entityType(), outcome.entityId(), outcome.updatedNaturalKey());
                            case SKIPPED -> rowEntity.markSkipped(outcome.entityType(), outcome.entityId());
                            case ERROR -> rowEntity.markExecutionError(
                                    outcome.errorCode(), outcome.errorField(), outcome.errorMessage());
                            default -> {
                            }
                        }
                        updatedRows.add(rowEntity);
                    }
                    if (!updatedRows.isEmpty()) {
                        rowRepository.saveAll(updatedRows);
                    }

                    Instant executedAt = clock.instant();
                    job.completeExecution(
                            execResult.finalStatus(),
                            execResult.validRows(),
                            execResult.errorRows(),
                            execResult.duplicateRows(),
                            execResult.createdCount(),
                            execResult.updatedCount(),
                            execResult.skippedCount(),
                            actorId,
                            executedAt);
                    ImportJob saved = jobRepository.save(job);

                    ImportJobEventType eventType = switch (execResult.finalStatus()) {
                        case COMPLETED -> ImportJobEventType.COMPLETED;
                        case COMPLETED_WITH_ERRORS -> ImportJobEventType.COMPLETED_WITH_ERRORS;
                        default -> ImportJobEventType.FAILED;
                    };
                    int nextSeq = eventRepository.findMaxSequenceNoByJobId(saved.getId()) + 1;
                    eventRepository.save(new ImportJobEvent(
                            saved.getId(),
                            nextSeq,
                            eventType,
                            actorId,
                            "Exécution terminée (" + saved.getStatus() + ") : "
                                    + saved.getCreatedCount() + " créés, "
                                    + saved.getUpdatedCount() + " mis à jour, "
                                    + saved.getSkippedCount() + " ignorés, "
                                    + saved.getErrorRows() + " erreurs.",
                            executedAt));

                    AuditAction auditAction = saved.getStatus() == ImportJobStatus.FAILED
                            ? AuditAction.SCHOOLIMPORT_JOB_FAILED
                            : AuditAction.SCHOOLIMPORT_JOB_EXECUTED;
                    auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                            AuditCategory.SCHOOLIMPORT,
                            auditAction,
                            "SCHOOLIMPORT_JOB",
                            saved.getId(),
                            "Finalisation d'un lot d'import massif (" + saved.getStatus() + ").",
                            Map.of(
                                    "jobNumber", saved.getJobNumber(),
                                    "importType", saved.getImportType().name(),
                                    "status", saved.getStatus().name(),
                                    "createdCount", saved.getCreatedCount(),
                                    "updatedCount", saved.getUpdatedCount(),
                                    "skippedCount", saved.getSkippedCount(),
                                    "errorRows", saved.getErrorRows())));
                    return saved;
                }));

        return new ImportJobResult(finalized, false);
    }

    @PreAuthorize(SchoolImportRoles.WRITE_ROLES)
    @Transactional
    public ImportJob cancelJob(UUID jobId, String reason, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        ImportJob job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new SchoolImportNotFoundException("Lot d'import introuvable."));
        accessGuard.assertCanMutateJob(job, actorId);

        Instant now = clock.instant();
        job.cancel(reason, actorId, now);
        ImportJob saved = jobRepository.save(job);

        int nextSeq = eventRepository.findMaxSequenceNoByJobId(saved.getId()) + 1;
        eventRepository.save(new ImportJobEvent(
                saved.getId(),
                nextSeq,
                ImportJobEventType.CANCELLED,
                actorId,
                "Lot d'import annulé : " + saved.getCancellationReason(),
                now));

        auditLogPort.recordTenantEvent(AuditRecordCommand.warning(
                AuditCategory.SCHOOLIMPORT,
                AuditAction.SCHOOLIMPORT_JOB_CANCELLED,
                AuditOutcome.SUCCESS,
                "SCHOOLIMPORT_JOB",
                saved.getId(),
                "Annulation d'un lot d'import massif.",
                Map.of("jobNumber", saved.getJobNumber(), "importType", saved.getImportType().name())));

        return saved;
    }

    @PreAuthorize(SchoolImportRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<ImportJob> listJobs(
            ImportType importType,
            ImportJobStatus status,
            UUID academicYearId,
            UUID actorId,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        boolean restrictToOwn = accessGuard.isRestrictedTeacher();

        Specification<ImportJob> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (restrictToOwn) {
                predicates.add(cb.equal(root.get("createdBy"), actorId));
            }
            if (importType != null) {
                predicates.add(cb.equal(root.get("importType"), importType));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (academicYearId != null) {
                predicates.add(cb.equal(root.get("academicYearId"), academicYearId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return jobRepository.findAll(spec, pageable);
    }

    @PreAuthorize(SchoolImportRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public ImportJob getJob(UUID jobId, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        ImportJob job = requireJob(jobId);
        accessGuard.assertCanReadJob(job, actorId);
        return job;
    }

    @PreAuthorize(SchoolImportRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<ImportJobRow> listJobRows(
            UUID jobId,
            ImportRowStatus rowStatus,
            ImportRowAction plannedAction,
            UUID actorId,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        ImportJob job = requireJob(jobId);
        accessGuard.assertCanReadJob(job, actorId);

        if (rowStatus != null && plannedAction != null) {
            return rowRepository.findByJobIdAndRowStatusAndPlannedAction(jobId, rowStatus, plannedAction, pageable);
        }
        if (rowStatus != null) {
            return rowRepository.findByJobIdAndRowStatus(jobId, rowStatus, pageable);
        }
        if (plannedAction != null) {
            return rowRepository.findByJobIdAndPlannedAction(jobId, plannedAction, pageable);
        }
        return rowRepository.findByJobId(jobId, pageable);
    }

    @PreAuthorize(SchoolImportRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public List<ImportJobEvent> listJobEvents(UUID jobId, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        ImportJob job = requireJob(jobId);
        accessGuard.assertCanReadJob(job, actorId);
        return eventRepository.findByJobIdOrderBySequenceNoAsc(jobId);
    }

    @PreAuthorize(SchoolImportRoles.READ_ROLES)
    @Transactional
    public DownloadableCsv downloadErrorReport(UUID jobId, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        ImportJob job = requireJob(jobId);
        accessGuard.assertCanReadJob(job, actorId);

        List<ImportJobRow> errorRows = rowRepository.findByJobIdAndRowStatusOrderByRowNumberAsc(
                jobId, ImportRowStatus.ERROR);
        byte[] csv = ImportErrorReportFormatter.formatCsv(errorRows);
        String fileName = ImportFileSecurityPolicy.buildSafeCsvDownloadName("erreurs-import", job.getJobNumber());

        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.SCHOOLIMPORT,
                AuditAction.SCHOOLIMPORT_ERROR_REPORT_DOWNLOADED,
                "SCHOOLIMPORT_JOB",
                job.getId(),
                "Téléchargement du rapport CSV d'erreurs d'un lot d'import.",
                Map.of("jobNumber", job.getJobNumber(), "errorCount", errorRows.size())));
        return new DownloadableCsv(fileName, csv);
    }

    @PreAuthorize(SchoolImportRoles.SUMMARY_ROLES)
    @Transactional(readOnly = true)
    public SchoolImportSummary getSummary() {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        List<Object[]> totalsList = jobRepository.aggregateSummaryTotals();
        Object[] t = totalsList.isEmpty() ? null : totalsList.getFirst();

        long totalJobs = asLong(t, 0);
        long previewReady = asLong(t, 1);
        long validationFailed = asLong(t, 2);
        long completed = asLong(t, 3);
        long completedWithErrors = asLong(t, 4);
        long failed = asLong(t, 5);
        long cancelled = asLong(t, 6);
        long totalRows = asLong(t, 7);
        long createdRows = asLong(t, 8);
        long updatedRows = asLong(t, 9);
        long skippedRows = asLong(t, 10);
        long errorRows = asLong(t, 11);
        long storageBytes = asLong(t, 12);

        Map<ImportType, Long> byType = new EnumMap<>(ImportType.class);
        for (ImportType type : ImportType.values()) {
            byType.put(type, 0L);
        }
        for (Object[] row : jobRepository.countGroupedByImportType()) {
            if (row != null && row.length >= 2 && row[0] instanceof ImportType type && row[1] instanceof Number n) {
                byType.put(type, n.longValue());
            }
        }

        return new SchoolImportSummary(
                totalJobs,
                previewReady,
                validationFailed,
                completed,
                completedWithErrors,
                failed,
                cancelled,
                totalRows,
                createdRows,
                updatedRows,
                skippedRows,
                errorRows,
                storageBytes,
                Map.copyOf(byType));
    }

    private ImportJob requireJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new SchoolImportNotFoundException("Lot d'import introuvable."));
    }

    private static ImportJobStatus determinePreviewStatus(
            ImportValidationMode validationMode, AnalysisResult analysis) {
        if (validationMode == ImportValidationMode.STRICT_ALL_OR_NOTHING && analysis.errorRows() > 0) {
            return ImportJobStatus.VALIDATION_FAILED;
        }
        if (analysis.validRows() == 0 && analysis.duplicateRows() == 0 && analysis.errorRows() > 0) {
            return ImportJobStatus.VALIDATION_FAILED;
        }
        return ImportJobStatus.PREVIEW_READY;
    }

    private static String requireIdempotencyKey(String rawKey, String headerName) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new SchoolImportValidationException(
                    "schoolimport-idempotency-key-missing",
                    "Clé d'idempotence obligatoire",
                    "L'en-tête ou champ '" + headerName + "' est obligatoire pour garantir l'idempotence.");
        }
        String trimmed = rawKey.trim();
        if (trimmed.length() > 128) {
            throw new SchoolImportValidationException(
                    "La clé d'idempotence ne doit pas dépasser 128 caractères.");
        }
        return trimmed;
    }

    private static String computeCreateRequestHash(
            ImportType importType,
            ImportValidationMode validationMode,
            DuplicateStrategy duplicateStrategy,
            UUID academicYearId,
            UUID evaluationId,
            UUID attendanceSessionId,
            boolean executeImmediately,
            String fileSha256) {
        String canonical = String.join("|",
                importType.name(),
                validationMode.name(),
                duplicateStrategy.name(),
                academicYearId != null ? academicYearId.toString() : "",
                evaluationId != null ? evaluationId.toString() : "",
                attendanceSessionId != null ? attendanceSessionId.toString() : "",
                Boolean.toString(executeImmediately),
                fileSha256.toLowerCase(Locale.ROOT));
        return ImportFileSecurityPolicy.sha256Hex(canonical);
    }

    private static long asLong(Object[] row, int idx) {
        if (row == null || idx >= row.length || !(row[idx] instanceof Number n)) {
            return 0L;
        }
        return n.longValue();
    }
}
