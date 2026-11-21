package com.ecole.saas.reporting.domain;

import com.ecole.saas.reporting.ReportFormat;
import com.ecole.saas.reporting.ReportScopeType;
import com.ecole.saas.reporting.ReportSnapshotStatus;
import com.ecole.saas.reporting.ReportType;
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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot persisté d'un rapport consolidé (numérotation officielle, idempotence, empreinte SHA-256
 * et archivage logique uniquement).
 */
@Entity
@Table(name = "reporting_snapshots")
public class ReportSnapshot extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "snapshot_number", nullable = false, updatable = false, length = 64)
    private String snapshotNumber;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, updatable = false, length = 30)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_format", nullable = false, updatable = false, length = 10)
    private ReportFormat outputFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportSnapshotStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, updatable = false, length = 20)
    private ReportScopeType scopeType;

    @Column(name = "academic_year_id", updatable = false)
    private UUID academicYearId;

    @Column(name = "evaluation_period_id", updatable = false)
    private UUID evaluationPeriodId;

    @Column(name = "class_room_id", updatable = false)
    private UUID classRoomId;

    @Column(name = "student_id", updatable = false)
    private UUID studentId;

    @Column(name = "from_date", updatable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", updatable = false)
    private LocalDate toDate;

    @Column(name = "title", nullable = false, updatable = false, length = 200)
    private String title;

    @Column(name = "file_name", nullable = false, updatable = false, length = 200)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false, length = 120)
    private String contentType;

    @Column(name = "storage_object_key", nullable = false, updatable = false, length = 300)
    private String storageObjectKey;

    @Column(name = "file_size_bytes", nullable = false, updatable = false)
    private long fileSizeBytes;

    @Column(name = "content_sha256", nullable = false, updatable = false, length = 64)
    private String contentSha256;

    @Column(name = "summary_json", updatable = false)
    private String summaryJson;

    @Column(name = "generated_by", nullable = false, updatable = false)
    private UUID generatedBy;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    @Column(name = "archive_reason", length = 500)
    private String archiveReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ReportSnapshot() {
    }

    public ReportSnapshot(
            String snapshotNumber,
            String idempotencyKey,
            String requestHash,
            ReportType reportType,
            ReportFormat outputFormat,
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
            String storageObjectKey,
            long fileSizeBytes,
            String contentSha256,
            String summaryJson,
            UUID generatedBy,
            Instant generatedAt) {
        this.snapshotNumber = requireNonBlank(snapshotNumber, "Le numéro de snapshot est obligatoire.", 64);
        this.idempotencyKey = ReportSecurityPolicy.normalizeIdempotencyKey(idempotencyKey);
        this.requestHash = requireNonBlank(requestHash, "L'empreinte de requête est obligatoire.", 64);
        this.reportType = Objects.requireNonNull(reportType, "Le type de rapport est obligatoire.");
        this.outputFormat = Objects.requireNonNull(outputFormat, "Le format de sortie est obligatoire.");
        this.status = ReportSnapshotStatus.READY;
        this.scopeType = Objects.requireNonNull(scopeType, "Le périmètre du rapport est obligatoire.");
        this.academicYearId = academicYearId;
        this.evaluationPeriodId = evaluationPeriodId;
        this.classRoomId = classRoomId;
        this.studentId = studentId;
        ReportingMetricsCalculator.validateDateRange(fromDate, toDate);
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.title = requireNonBlank(title, "Le titre du snapshot est obligatoire.", 200);
        this.fileName = requireNonBlank(fileName, "Le nom de fichier du snapshot est obligatoire.", 200);
        this.contentType = requireNonBlank(contentType, "Le type MIME est obligatoire.", 120);
        ReportSecurityPolicy.validateStorageObjectKey(storageObjectKey);
        this.storageObjectKey = storageObjectKey;
        if (fileSizeBytes <= 0L || fileSizeBytes > ReportSecurityPolicy.MAX_SNAPSHOT_SIZE_BYTES) {
            throw new ReportingValidationException(
                    "La taille du snapshot doit être comprise entre 1 octet et 5 Mo.");
        }
        this.fileSizeBytes = fileSizeBytes;
        this.contentSha256 = requireNonBlank(contentSha256, "L'empreinte SHA-256 est obligatoire.", 64);
        this.summaryJson = summaryJson;
        this.generatedBy = Objects.requireNonNull(generatedBy, "L'auteur de la génération est obligatoire.");
        this.generatedAt = Objects.requireNonNull(generatedAt, "La date de génération est obligatoire.");
    }

    public void archive(UUID actorId, Instant now, String reason) {
        if (this.status == ReportSnapshotStatus.ARCHIVED) {
            throw new ReportingConflictException(
                    "reporting-snapshot-already-archived",
                    "Ce snapshot de rapport est déjà archivé.");
        }
        if (reason == null || reason.isBlank()) {
            throw new ReportingValidationException("Le motif d'archivage est obligatoire.");
        }
        String trimmed = reason.trim();
        if (trimmed.length() < 5 || trimmed.length() > 500) {
            throw new ReportingValidationException(
                    "Le motif d'archivage doit contenir entre 5 et 500 caractères.");
        }
        this.status = ReportSnapshotStatus.ARCHIVED;
        this.archivedBy = Objects.requireNonNull(actorId, "L'acteur d'archivage est obligatoire.");
        this.archivedAt = Objects.requireNonNull(now, "L'horodatage d'archivage est obligatoire.");
        this.archiveReason = trimmed;
    }

    public boolean matchesRequestHash(String candidateHash) {
        return candidateHash != null && this.requestHash.equals(candidateHash);
    }

    public UUID getId() {
        return id;
    }

    public String getSnapshotNumber() {
        return snapshotNumber;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public ReportType getReportType() {
        return reportType;
    }

    public ReportFormat getOutputFormat() {
        return outputFormat;
    }

    public ReportSnapshotStatus getStatus() {
        return status;
    }

    public ReportScopeType getScopeType() {
        return scopeType;
    }

    public UUID getAcademicYearId() {
        return academicYearId;
    }

    public UUID getEvaluationPeriodId() {
        return evaluationPeriodId;
    }

    public UUID getClassRoomId() {
        return classRoomId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public String getTitle() {
        return title;
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

    public String getContentSha256() {
        return contentSha256;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public UUID getGeneratedBy() {
        return generatedBy;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public UUID getArchivedBy() {
        return archivedBy;
    }

    public String getArchiveReason() {
        return archiveReason;
    }

    public long getVersion() {
        return version;
    }

    private static String requireNonBlank(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ReportingValidationException(message);
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new ReportingValidationException(
                    "La valeur dépasse la longueur maximale autorisée (" + maxLength + " caractères).");
        }
        return trimmed;
    }
}
