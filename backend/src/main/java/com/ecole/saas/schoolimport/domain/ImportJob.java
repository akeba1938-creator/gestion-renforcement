package com.ecole.saas.schoolimport.domain;

import com.ecole.saas.schoolimport.DuplicateStrategy;
import com.ecole.saas.schoolimport.ImportJobStatus;
import com.ecole.saas.schoolimport.ImportSourceFormat;
import com.ecole.saas.schoolimport.ImportType;
import com.ecole.saas.schoolimport.ImportValidationMode;
import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agrégat principal d'un lot d'import massif scolaire (analyse à sec, prévisualisation,
 * exécution idempotente verrouillée et traçabilité complète).
 */
@Entity
@Table(name = "schoolimport_jobs")
public class ImportJob extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_number", nullable = false, updatable = false, length = 64)
    private String jobNumber;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "execution_idempotency_key", length = 128)
    private String executionIdempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_type", nullable = false, updatable = false, length = 30)
    private ImportType importType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_format", nullable = false, updatable = false, length = 10)
    private ImportSourceFormat sourceFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_mode", nullable = false, updatable = false, length = 30)
    private ImportValidationMode validationMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "duplicate_strategy", nullable = false, updatable = false, length = 25)
    private DuplicateStrategy duplicateStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ImportJobStatus status;

    @Column(name = "academic_year_id", updatable = false)
    private UUID academicYearId;

    @Column(name = "evaluation_id", updatable = false)
    private UUID evaluationId;

    @Column(name = "attendance_session_id", updatable = false)
    private UUID attendanceSessionId;

    @Column(name = "file_name", nullable = false, updatable = false, length = 200)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false, length = 120)
    private String contentType;

    @Column(name = "storage_object_key", nullable = false, updatable = false, length = 300)
    private String storageObjectKey;

    @Column(name = "file_size_bytes", nullable = false, updatable = false)
    private long fileSizeBytes;

    @Column(name = "file_sha256", nullable = false, updatable = false, length = 64)
    private String fileSha256;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "error_rows", nullable = false)
    private int errorRows;

    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "quota_resource", length = 25)
    private String quotaResource;

    @Column(name = "quota_limit")
    private Long quotaLimit;

    @Column(name = "quota_used_before")
    private Long quotaUsedBefore;

    @Column(name = "quota_projected_after")
    private Long quotaProjectedAfter;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "analyzed_at", nullable = false, updatable = false)
    private Instant analyzedAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "executed_by")
    private UUID executedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ImportJob() {
    }

    public ImportJob(
            String jobNumber,
            String idempotencyKey,
            String requestHash,
            ImportType importType,
            ImportSourceFormat sourceFormat,
            ImportValidationMode validationMode,
            DuplicateStrategy duplicateStrategy,
            ImportJobStatus initialStatus,
            UUID academicYearId,
            UUID evaluationId,
            UUID attendanceSessionId,
            String fileName,
            String contentType,
            String storageObjectKey,
            long fileSizeBytes,
            String fileSha256,
            int totalRows,
            int validRows,
            int errorRows,
            int duplicateRows,
            String quotaResource,
            Long quotaLimit,
            Long quotaUsedBefore,
            Long quotaProjectedAfter,
            UUID createdBy,
            Instant analyzedAt) {
        this.jobNumber = Objects.requireNonNull(jobNumber, "Le numéro de lot est obligatoire.");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "La clé d'idempotence est obligatoire.");
        this.requestHash = Objects.requireNonNull(requestHash, "L'empreinte de requête est obligatoire.");
        this.importType = Objects.requireNonNull(importType, "Le type d'import est obligatoire.");
        this.sourceFormat = Objects.requireNonNull(sourceFormat, "Le format source est obligatoire.");
        this.validationMode = Objects.requireNonNull(validationMode, "Le mode de validation est obligatoire.");
        this.duplicateStrategy = Objects.requireNonNull(duplicateStrategy, "La stratégie de doublons est obligatoire.");
        this.status = Objects.requireNonNull(initialStatus, "Le statut initial est obligatoire.");
        this.academicYearId = academicYearId;
        this.evaluationId = evaluationId;
        this.attendanceSessionId = attendanceSessionId;
        this.fileName = Objects.requireNonNull(fileName, "Le nom de fichier est obligatoire.");
        this.contentType = Objects.requireNonNull(contentType, "Le type MIME est obligatoire.");
        this.storageObjectKey = Objects.requireNonNull(storageObjectKey, "La clé de stockage est obligatoire.");
        if (fileSizeBytes <= 0 || fileSizeBytes > ImportFileSecurityPolicy.MAX_FILE_SIZE_BYTES) {
            throw new SchoolImportValidationException("La taille du fichier est invalide.");
        }
        this.fileSizeBytes = fileSizeBytes;
        this.fileSha256 = Objects.requireNonNull(fileSha256, "L'empreinte SHA-256 est obligatoire.");
        this.totalRows = totalRows;
        this.validRows = validRows;
        this.errorRows = errorRows;
        this.duplicateRows = duplicateRows;
        this.quotaResource = quotaResource;
        this.quotaLimit = quotaLimit;
        this.quotaUsedBefore = quotaUsedBefore;
        this.quotaProjectedAfter = quotaProjectedAfter;
        this.createdBy = Objects.requireNonNull(createdBy, "L'acteur créateur est obligatoire.");
        this.analyzedAt = Objects.requireNonNull(analyzedAt, "L'horodatage d'analyse est obligatoire.");
    }

    public void beginProcessing(String executionIdempotencyKey) {
        if (!this.status.isExecutable()) {
            throw new SchoolImportConflictException(
                    "schoolimport-job-not-executable",
                    "Statut de lot incompatible",
                    "Ce lot d'import ne peut pas être exécuté car son statut courant est " + this.status + ".");
        }
        if (executionIdempotencyKey == null || executionIdempotencyKey.isBlank()) {
            throw new SchoolImportValidationException("La clé d'idempotence d'exécution est obligatoire.");
        }
        this.executionIdempotencyKey = executionIdempotencyKey.trim();
        this.status = ImportJobStatus.PROCESSING;
    }

    public void completeExecution(
            ImportJobStatus finalStatus,
            int validRows,
            int errorRows,
            int duplicateRows,
            int createdCount,
            int updatedCount,
            int skippedCount,
            UUID actorId,
            Instant executedAt) {
        if (this.status != ImportJobStatus.PROCESSING) {
            throw new SchoolImportConflictException(
                    "schoolimport-invalid-transition",
                    "Transition de statut invalide pour la finalisation du lot.");
        }
        if (finalStatus != ImportJobStatus.COMPLETED
                && finalStatus != ImportJobStatus.COMPLETED_WITH_ERRORS
                && finalStatus != ImportJobStatus.FAILED) {
            throw new SchoolImportValidationException("Statut final d'exécution invalide : " + finalStatus);
        }
        this.status = finalStatus;
        this.validRows = validRows;
        this.errorRows = errorRows;
        this.duplicateRows = duplicateRows;
        this.createdCount = createdCount;
        this.updatedCount = updatedCount;
        this.skippedCount = skippedCount;
        this.executedBy = Objects.requireNonNull(actorId, "L'acteur d'exécution est obligatoire.");
        this.executedAt = Objects.requireNonNull(executedAt, "L'horodatage d'exécution est obligatoire.");
    }

    public void cancel(String reason, UUID actorId, Instant cancelledAt) {
        if (!this.status.isCancellable()) {
            throw new SchoolImportConflictException(
                    "schoolimport-job-not-cancellable",
                    "Annulation impossible",
                    "Un lot au statut " + this.status + " ne peut plus être annulé.");
        }
        if (reason == null || reason.trim().length() < 5 || reason.trim().length() > 500) {
            throw new SchoolImportValidationException(
                    "Le motif d'annulation est obligatoire et doit contenir entre 5 et 500 caractères.");
        }
        this.status = ImportJobStatus.CANCELLED;
        this.cancellationReason = reason.trim();
        this.cancelledBy = Objects.requireNonNull(actorId, "L'acteur d'annulation est obligatoire.");
        this.cancelledAt = Objects.requireNonNull(cancelledAt, "L'horodatage d'annulation est obligatoire.");
    }

    public UUID getId() {
        return id;
    }

    public String getJobNumber() {
        return jobNumber;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getExecutionIdempotencyKey() {
        return executionIdempotencyKey;
    }

    public ImportType getImportType() {
        return importType;
    }

    public ImportSourceFormat getSourceFormat() {
        return sourceFormat;
    }

    public ImportValidationMode getValidationMode() {
        return validationMode;
    }

    public DuplicateStrategy getDuplicateStrategy() {
        return duplicateStrategy;
    }

    public ImportJobStatus getStatus() {
        return status;
    }

    public UUID getAcademicYearId() {
        return academicYearId;
    }

    public UUID getEvaluationId() {
        return evaluationId;
    }

    public UUID getAttendanceSessionId() {
        return attendanceSessionId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public String getStorageObjectKey() {
        return storageObjectKey;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getValidRows() {
        return validRows;
    }

    public int getErrorRows() {
        return errorRows;
    }

    public int getDuplicateRows() {
        return duplicateRows;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public String getQuotaResource() {
        return quotaResource;
    }

    public Long getQuotaLimit() {
        return quotaLimit;
    }

    public Long getQuotaUsedBefore() {
        return quotaUsedBefore;
    }

    public Long getQuotaProjectedAfter() {
        return quotaProjectedAfter;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public UUID getExecutedBy() {
        return executedBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public UUID getCancelledBy() {
        return cancelledBy;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public long getVersion() {
        return version;
    }
}
