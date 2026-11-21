package com.ecole.saas.schoolimport.domain;

import com.ecole.saas.schoolimport.ImportSourceFormat;
import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Fichier source binaire d'un lot d'import — STRICTEMENT IMMUABLE EN AJOUT SEUL.
 * Isolé par {@code tenant_id} (Hibernate {@code @TenantId} + RLS PostgreSQL forcée fail-closed)
 * et protégé par trigger SQL {@code BEFORE UPDATE OR DELETE}.
 */
@Entity
@Table(name = "schoolimport_job_files")
public class ImportJobFile extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "storage_object_key", nullable = false, updatable = false, length = 300)
    private String storageObjectKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 120)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "detected_format", nullable = false, updatable = false, length = 10)
    private ImportSourceFormat detectedFormat;

    @Column(name = "file_size_bytes", nullable = false, updatable = false)
    private long fileSizeBytes;

    @Column(name = "sha256_hash", nullable = false, updatable = false, length = 64)
    private String sha256Hash;

    @Column(name = "content", nullable = false, updatable = false)
    private byte[] content;

    protected ImportJobFile() {
    }

    public ImportJobFile(
            UUID jobId,
            String storageObjectKey,
            String contentType,
            ImportSourceFormat detectedFormat,
            String sha256Hash,
            byte[] content) {
        this.jobId = Objects.requireNonNull(jobId, "L'identifiant du lot est obligatoire.");
        if (storageObjectKey == null || storageObjectKey.isBlank() || storageObjectKey.contains("..")) {
            throw new SchoolImportValidationException("La clé de stockage est invalide.");
        }
        this.storageObjectKey = storageObjectKey;
        this.contentType = Objects.requireNonNull(contentType, "Le type MIME est obligatoire.");
        this.detectedFormat = Objects.requireNonNull(detectedFormat, "Le format détecté est obligatoire.");
        if (content == null || content.length == 0 || content.length > ImportFileSecurityPolicy.MAX_FILE_SIZE_BYTES) {
            throw new SchoolImportValidationException("Le contenu binaire du fichier d'import est invalide.");
        }
        String computed = ImportFileSecurityPolicy.sha256Hex(content);
        if (!computed.equalsIgnoreCase(sha256Hash)) {
            throw new SchoolImportValidationException("L'empreinte SHA-256 du fichier d'import est incohérente.");
        }
        this.sha256Hash = computed;
        this.content = Arrays.copyOf(content, content.length);
        this.fileSizeBytes = this.content.length;
    }

    @Override
    @PreUpdate
    protected void onUpdate() {
        throw new UnsupportedOperationException("Le fichier source d'un lot d'import est strictement immuable.");
    }

    @PreRemove
    protected void onRemove() {
        throw new UnsupportedOperationException("Le fichier source d'un lot d'import ne peut jamais être supprimé.");
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public String getStorageObjectKey() {
        return storageObjectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public ImportSourceFormat getDetectedFormat() {
        return detectedFormat;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public byte[] getContent() {
        return content != null ? Arrays.copyOf(content, content.length) : new byte[0];
    }
}
