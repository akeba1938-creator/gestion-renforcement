package com.ecole.saas.schooldocs.domain;

import com.ecole.saas.schooldocs.DocumentStatus;
import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schooldocs.DocumentValidityStatus;
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
 * Document scolaire ou administratif officiel émis par un établissement (original ou duplicata tracé).
 * Les champs officiels rendus et l'empreinte binaire sont immuables après émission ;
 * seule la révocation ({@code ISSUED -> REVOKED}) avec motif explicite est autorisée.
 */
@Entity
@Table(name = "schooldocs_documents")
public class IssuedDocument extends TenantScopedEntity {

    private static final int MIN_REASON_LENGTH = 5;
    private static final int MAX_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_number", nullable = false, updatable = false, length = 64)
    private String documentNumber;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "verification_token", nullable = false, updatable = false, length = 128)
    private String verificationToken;

    @Column(name = "template_id", updatable = false)
    private UUID templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false, length = 40)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "original_document_id", updatable = false)
    private UUID originalDocumentId;

    @Column(name = "edition_number", nullable = false, updatable = false)
    private int editionNumber;

    @Column(name = "is_duplicate", nullable = false, updatable = false)
    private boolean duplicate;

    @Column(name = "reissue_reason", updatable = false, length = 500)
    private String reissueReason;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "enrollment_id", updatable = false)
    private UUID enrollmentId;

    @Column(name = "guardian_id", updatable = false)
    private UUID guardianId;

    @Column(name = "academic_year_id", updatable = false)
    private UUID academicYearId;

    @Column(name = "class_room_id", updatable = false)
    private UUID classRoomId;

    @Column(name = "title", nullable = false, updatable = false, length = 255)
    private String title;

    @Column(name = "rendered_body", nullable = false, updatable = false)
    private String renderedBody;

    @Column(name = "rendered_footer", updatable = false, length = 1000)
    private String renderedFooter;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "file_name", nullable = false, updatable = false, length = 200)
    private String fileName;

    @Column(name = "storage_object_key", nullable = false, updatable = false, length = 300)
    private String storageObjectKey;

    @Column(name = "file_size_bytes", nullable = false, updatable = false)
    private long fileSizeBytes;

    @Column(name = "content_sha256", nullable = false, updatable = false, length = 64)
    private String contentSha256;

    @Column(name = "fee_amount_xof", nullable = false, updatable = false)
    private long feeAmountXof;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "issued_by", nullable = false, updatable = false)
    private UUID issuedBy;

    @Column(name = "valid_until", updatable = false)
    private Instant validUntil;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected IssuedDocument() {
    }

    private IssuedDocument(
            String documentNumber,
            String idempotencyKey,
            String requestHash,
            String verificationToken,
            UUID templateId,
            DocumentType documentType,
            UUID originalDocumentId,
            int editionNumber,
            boolean duplicate,
            String reissueReason,
            UUID studentId,
            UUID enrollmentId,
            UUID guardianId,
            UUID academicYearId,
            UUID classRoomId,
            String title,
            String renderedBody,
            String renderedFooter,
            String contentType,
            String fileName,
            String storageObjectKey,
            long fileSizeBytes,
            String contentSha256,
            long feeAmountXof,
            Instant issuedAt,
            UUID issuedBy,
            Instant validUntil) {
        this.documentNumber = requireNonBlank(documentNumber, "Le numéro officiel du document", 64);
        this.idempotencyKey = requireNonBlank(idempotencyKey, "La clé d'idempotence", 128);
        this.requestHash = requireNonBlank(requestHash, "L'empreinte de requête", 64);
        this.verificationToken = requireNonBlank(verificationToken, "Le jeton opaque de vérification", 128);
        this.templateId = templateId;
        this.documentType = Objects.requireNonNull(documentType, "Le type de document est obligatoire.");
        this.status = DocumentStatus.ISSUED;
        if (duplicate) {
            this.originalDocumentId = Objects.requireNonNull(
                    originalDocumentId, "Le document d'origine est obligatoire pour un duplicata.");
            if (editionNumber < 2) {
                throw new SchoolDocValidationException("Le numéro d'édition d'un duplicata doit être >= 2.");
            }
            this.editionNumber = editionNumber;
            this.duplicate = true;
            this.reissueReason = requireReason(reissueReason, "Le motif de réédition/duplicata");
        } else {
            if (originalDocumentId != null || editionNumber != 1) {
                throw new SchoolDocValidationException("Un document original porte le numéro d'édition 1.");
            }
            this.originalDocumentId = null;
            this.editionNumber = 1;
            this.duplicate = false;
            this.reissueReason = null;
        }
        this.studentId = Objects.requireNonNull(studentId, "L'élève est obligatoire.");
        this.enrollmentId = enrollmentId;
        this.guardianId = guardianId;
        this.academicYearId = academicYearId;
        this.classRoomId = classRoomId;
        this.title = requireNonBlank(title, "Le titre du document", 255);
        this.renderedBody = requireBody(renderedBody);
        this.renderedFooter = normalizeOptional(renderedFooter, 1000);
        this.contentType = requireNonBlank(contentType, "Le type MIME du document", 100);
        this.fileName = requireNonBlank(fileName, "Le nom de fichier", 200);
        DocumentSecurityPolicy.validateStorageObjectKey(storageObjectKey);
        this.storageObjectKey = storageObjectKey;
        if (fileSizeBytes <= 0L || fileSizeBytes > DocumentSecurityPolicy.MAX_DOCUMENT_SIZE_BYTES) {
            throw new SchoolDocValidationException("La taille du fichier généré est hors limites autorisées.");
        }
        this.fileSizeBytes = fileSizeBytes;
        this.contentSha256 = requireNonBlank(contentSha256, "L'empreinte SHA-256 du contenu", 64);
        if (feeAmountXof < 0L) {
            throw new SchoolDocValidationException("Les frais de document en FCFA doivent être positifs ou nuls.");
        }
        this.feeAmountXof = feeAmountXof;
        this.issuedAt = Objects.requireNonNull(issuedAt, "La date d'émission est obligatoire.");
        this.issuedBy = Objects.requireNonNull(issuedBy, "L'émetteur est obligatoire.");
        if (validUntil != null && !validUntil.isAfter(issuedAt)) {
            throw new SchoolDocValidationException(
                    "La date de fin de validité doit être strictement postérieure à la date d'émission.");
        }
        this.validUntil = validUntil;
    }

    public static IssuedDocument issueOriginal(
            String documentNumber,
            String idempotencyKey,
            String requestHash,
            String verificationToken,
            UUID templateId,
            DocumentType documentType,
            UUID studentId,
            UUID enrollmentId,
            UUID guardianId,
            UUID academicYearId,
            UUID classRoomId,
            String title,
            String renderedBody,
            String renderedFooter,
            String contentType,
            String fileName,
            String storageObjectKey,
            long fileSizeBytes,
            String contentSha256,
            long feeAmountXof,
            Instant issuedAt,
            UUID issuedBy,
            Instant validUntil) {
        return new IssuedDocument(
                documentNumber,
                idempotencyKey,
                requestHash,
                verificationToken,
                templateId,
                documentType,
                null,
                1,
                false,
                null,
                studentId,
                enrollmentId,
                guardianId,
                academicYearId,
                classRoomId,
                title,
                renderedBody,
                renderedFooter,
                contentType,
                fileName,
                storageObjectKey,
                fileSizeBytes,
                contentSha256,
                feeAmountXof,
                issuedAt,
                issuedBy,
                validUntil);
    }

    public static IssuedDocument issueDuplicate(
            IssuedDocument originalRoot,
            String documentNumber,
            String idempotencyKey,
            String requestHash,
            String verificationToken,
            int editionNumber,
            String reissueReason,
            String fileName,
            String storageObjectKey,
            long fileSizeBytes,
            String contentSha256,
            long feeAmountXof,
            Instant issuedAt,
            UUID issuedBy,
            Instant validUntil) {
        Objects.requireNonNull(originalRoot, "Le document d'origine est obligatoire.");
        if (originalRoot.getStatus() == DocumentStatus.REVOKED) {
            throw new SchoolDocConflictException(
                    "document-revoked",
                    "Impossible d'émettre un duplicata pour un document révoqué.");
        }
        UUID rootId = originalRoot.getOriginalDocumentId() != null
                ? originalRoot.getOriginalDocumentId()
                : originalRoot.getId();
        return new IssuedDocument(
                documentNumber,
                idempotencyKey,
                requestHash,
                verificationToken,
                originalRoot.getTemplateId(),
                originalRoot.getDocumentType(),
                rootId,
                editionNumber,
                true,
                reissueReason,
                originalRoot.getStudentId(),
                originalRoot.getEnrollmentId(),
                originalRoot.getGuardianId(),
                originalRoot.getAcademicYearId(),
                originalRoot.getClassRoomId(),
                originalRoot.getTitle(),
                originalRoot.getRenderedBody(),
                originalRoot.getRenderedFooter(),
                DocumentSecurityPolicy.PDF_CONTENT_TYPE,
                fileName,
                storageObjectKey,
                fileSizeBytes,
                contentSha256,
                feeAmountXof,
                issuedAt,
                issuedBy,
                validUntil);
    }

    public void revoke(UUID actorId, String reason, Instant now) {
        Objects.requireNonNull(actorId, "L'acteur de la révocation est obligatoire.");
        Objects.requireNonNull(now, "L'horodatage de révocation est obligatoire.");
        if (this.status == DocumentStatus.REVOKED) {
            throw new SchoolDocConflictException(
                    "document-already-revoked",
                    "Ce document officiel a déjà été révoqué.");
        }
        this.revocationReason = requireReason(reason, "Le motif de révocation");
        this.revokedBy = actorId;
        this.revokedAt = now;
        this.status = DocumentStatus.REVOKED;
    }

    public DocumentValidityStatus evaluateValidity(Instant referenceTime) {
        Objects.requireNonNull(referenceTime, "L'horodatage de référence est obligatoire.");
        if (status == DocumentStatus.REVOKED) {
            return DocumentValidityStatus.REVOKED;
        }
        if (validUntil != null && referenceTime.isAfter(validUntil)) {
            return DocumentValidityStatus.EXPIRED;
        }
        return DocumentValidityStatus.VALID;
    }

    public UUID rootDocumentId() {
        return originalDocumentId != null ? originalDocumentId : id;
    }

    private static String requireReason(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new SchoolDocValidationException(label + " est obligatoire.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() < MIN_REASON_LENGTH || trimmed.length() > MAX_REASON_LENGTH) {
            throw new SchoolDocValidationException(
                    label + " doit contenir entre " + MIN_REASON_LENGTH + " et " + MAX_REASON_LENGTH + " caractères.");
        }
        return trimmed;
    }

    private static String requireNonBlank(String raw, String label, int maxLength) {
        if (raw == null || raw.isBlank()) {
            throw new SchoolDocValidationException(label + " est obligatoire.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new SchoolDocValidationException(label + " ne doit pas dépasser " + maxLength + " caractères.");
        }
        return trimmed;
    }

    private static String requireBody(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new SchoolDocValidationException("Le contenu rendu du document est obligatoire.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 30000) {
            throw new SchoolDocValidationException("Le contenu rendu du document dépasse la taille maximale.");
        }
        return trimmed;
    }

    private static String normalizeOptional(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new SchoolDocValidationException("Le pied de page dépasse la limite de " + maxLength + " caractères.");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public UUID getOriginalDocumentId() {
        return originalDocumentId;
    }

    public int getEditionNumber() {
        return editionNumber;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public String getReissueReason() {
        return reissueReason;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getEnrollmentId() {
        return enrollmentId;
    }

    public UUID getGuardianId() {
        return guardianId;
    }

    public UUID getAcademicYearId() {
        return academicYearId;
    }

    public UUID getClassRoomId() {
        return classRoomId;
    }

    public String getTitle() {
        return title;
    }

    public String getRenderedBody() {
        return renderedBody;
    }

    public String getRenderedFooter() {
        return renderedFooter;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFileName() {
        return fileName;
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

    public long getFeeAmountXof() {
        return feeAmountXof;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public UUID getIssuedBy() {
        return issuedBy;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getRevokedBy() {
        return revokedBy;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public long getVersion() {
        return version;
    }
}
