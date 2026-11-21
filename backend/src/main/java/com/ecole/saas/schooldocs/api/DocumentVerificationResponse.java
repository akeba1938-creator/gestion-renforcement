package com.ecole.saas.schooldocs.api;

import com.ecole.saas.schooldocs.DocumentStatus;
import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schooldocs.DocumentValidityStatus;
import com.ecole.saas.schooldocs.application.IssuedDocumentService.DocumentVerificationView;
import java.time.Instant;
import java.util.UUID;

public record DocumentVerificationResponse(
        UUID documentId,
        String documentNumber,
        DocumentType documentType,
        DocumentStatus status,
        DocumentValidityStatus validityStatus,
        boolean authentic,
        boolean integrityVerified,
        boolean duplicate,
        int editionNumber,
        UUID originalDocumentId,
        String originalDocumentNumber,
        String reissueReason,
        String schoolCode,
        String schoolName,
        String studentFullName,
        String studentMatricule,
        String academicYearCode,
        String title,
        String contentSha256,
        Instant issuedAt,
        Instant validUntil,
        Instant revokedAt,
        String revocationReason) {

    public static DocumentVerificationResponse from(DocumentVerificationView view) {
        return new DocumentVerificationResponse(
                view.documentId(),
                view.documentNumber(),
                view.documentType(),
                view.status(),
                view.validityStatus(),
                view.authentic(),
                view.integrityVerified(),
                view.duplicate(),
                view.editionNumber(),
                view.originalDocumentId(),
                view.originalDocumentNumber(),
                view.reissueReason(),
                view.schoolCode(),
                view.schoolName(),
                view.studentFullName(),
                view.studentMatricule(),
                view.academicYearCode(),
                view.title(),
                view.contentSha256(),
                view.issuedAt(),
                view.validUntil(),
                view.revokedAt(),
                view.revocationReason());
    }
}
