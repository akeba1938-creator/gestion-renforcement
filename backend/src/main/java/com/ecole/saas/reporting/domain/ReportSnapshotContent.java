package com.ecole.saas.reporting.domain;

import com.ecole.saas.reporting.ReportFormat;
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
 * Contenu binaire privé (JSON/CSV) d'un snapshot de rapport — STRICTEMENT IMMUABLE (ajout seul).
 */
@Entity
@Table(name = "reporting_snapshot_contents")
public class ReportSnapshotContent extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private UUID snapshotId;

    @Column(name = "storage_object_key", nullable = false, updatable = false, length = 300)
    private String storageObjectKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 120)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_format", nullable = false, updatable = false, length = 10)
    private ReportFormat outputFormat;

    @Column(name = "file_size_bytes", nullable = false, updatable = false)
    private long fileSizeBytes;

    @Column(name = "sha256_hash", nullable = false, updatable = false, length = 64)
    private String sha256Hash;

    @Column(name = "content", nullable = false, updatable = false)
    private byte[] content;

    protected ReportSnapshotContent() {
    }

    public ReportSnapshotContent(
            UUID snapshotId,
            String storageObjectKey,
            String contentType,
            ReportFormat outputFormat,
            String sha256Hash,
            byte[] content) {
        this.snapshotId = Objects.requireNonNull(snapshotId, "L'identifiant de snapshot est obligatoire.");
        ReportSecurityPolicy.validateStorageObjectKey(storageObjectKey);
        this.storageObjectKey = storageObjectKey;
        if (contentType == null || contentType.isBlank()) {
            throw new ReportingValidationException("Le type MIME est obligatoire.");
        }
        this.contentType = contentType.trim();
        this.outputFormat = Objects.requireNonNull(outputFormat, "Le format de sortie est obligatoire.");
        ReportSecurityPolicy.assertIntegrity(content, sha256Hash);
        this.fileSizeBytes = content.length;
        this.sha256Hash = sha256Hash.trim();
        this.content = Arrays.copyOf(content, content.length);
    }

    @Override
    @PreUpdate
    protected void onUpdate() {
        throw new UnsupportedOperationException("Le contenu binaire d'un snapshot est strictement immuable.");
    }

    @PreRemove
    protected void onRemove() {
        throw new UnsupportedOperationException("Le contenu binaire d'un snapshot ne peut jamais être supprimé.");
    }

    public UUID getId() {
        return id;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public String getStorageObjectKey() {
        return storageObjectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public ReportFormat getOutputFormat() {
        return outputFormat;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public byte[] getContentCopy() {
        return content == null ? new byte[0] : Arrays.copyOf(content, content.length);
    }
}
