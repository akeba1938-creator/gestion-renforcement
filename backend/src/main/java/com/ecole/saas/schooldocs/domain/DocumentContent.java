package com.ecole.saas.schooldocs.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Contenu binaire PDF privé d'un document officiel — STRICTEMENT IMMUABLE EN AJOUT SEUL.
 * Isolé par {@code tenant_id} (Hibernate {@code @TenantId} + RLS PostgreSQL forcée fail-closed)
 * et protégé par trigger SQL {@code BEFORE UPDATE OR DELETE}.
 */
@Entity
@Table(name = "schooldocs_document_contents")
public class DocumentContent extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "storage_object_key", nullable = false, updatable = false, length = 300)
    private String storageObjectKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false, updatable = false)
    private long fileSizeBytes;

    @Column(name = "sha256_hash", nullable = false, updatable = false, length = 64)
    private String sha256Hash;

    @Column(name = "content", nullable = false, updatable = false)
    private byte[] content;

    protected DocumentContent() {
    }

    public DocumentContent(
            UUID documentId,
            String storageObjectKey,
            String contentType,
            String sha256Hash,
            byte[] content) {
        this.documentId = Objects.requireNonNull(documentId, "L'identifiant du document est obligatoire.");
        DocumentSecurityPolicy.validateStorageObjectKey(storageObjectKey);
        this.storageObjectKey = storageObjectKey;
        if (contentType == null || contentType.isBlank()) {
            throw new SchoolDocValidationException("Le type MIME du contenu binaire est obligatoire.");
        }
        this.contentType = contentType.trim();
        DocumentSecurityPolicy.assertIntegrity(content, sha256Hash);
        this.sha256Hash = sha256Hash.trim();
        this.content = Arrays.copyOf(content, content.length);
        this.fileSizeBytes = this.content.length;
    }

    @Override
    @PreUpdate
    protected void onUpdate() {
        throw new UnsupportedOperationException(
                "Le contenu binaire d'un document officiel est strictement immuable.");
    }

    @PreRemove
    protected void onRemove() {
        throw new UnsupportedOperationException(
                "Le contenu binaire d'un document officiel ne peut jamais être supprimé.");
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getStorageObjectKey() {
        return storageObjectKey;
    }

    public String getContentType() {
        return contentType;
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
