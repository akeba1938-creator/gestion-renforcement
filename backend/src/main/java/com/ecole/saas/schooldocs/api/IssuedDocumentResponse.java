package com.ecole.saas.schooldocs.api;

import com.ecole.saas.schooldocs.DocumentStatus;
import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schooldocs.DocumentValidityStatus;
import com.ecole.saas.schooldocs.application.IssuedDocumentService.DocumentView;
import com.ecole.saas.schooldocs.domain.IssuedDocument;
import java.time.Instant;
import java.util.UUID;

public record IssuedDocumentResponse(
        UUID id,
        String documentNumber,
        String verificationToken,
        UUID templateId,
        String templateCode,
        DocumentType documentType,
        DocumentStatus status,
        DocumentValidityStatus validityStatus,
        UUID originalDocumentId,
        int editionNumber,
        boolean duplicate,
        String reissueReason,
        UUID studentId,
        String studentFullName,
        String studentMatricule,
        UUID enrollmentId,
        UUID guardianId,
        String guardianFullName,
        UUID academicYearId,
        String academicYearCode,
        String academicYearLabel,
        UUID classRoomId,
        String classRoomCode,
        String classRoomName,
        String title,
        String renderedBody,
        String renderedFooter,
        String contentType,
        String fileName,
        long fileSizeBytes,
        String contentSha256,
        long feeAmountXof,
        Instant issuedAt,
        UUID issuedBy,
        Instant validUntil,
        Instant revokedAt,
        UUID revokedBy,
        String revocationReason) {

    public static IssuedDocumentResponse from(DocumentView view) {
        IssuedDocument doc = view.document();
        return new IssuedDocumentResponse(
                doc.getId(),
                doc.getDocumentNumber(),
                doc.getVerificationToken(),
                doc.getTemplateId(),
                view.templateCode(),
                doc.getDocumentType(),
                doc.getStatus(),
                view.validityStatus(),
                doc.getOriginalDocumentId(),
                doc.getEditionNumber(),
                doc.isDuplicate(),
                doc.getReissueReason(),
                doc.getStudentId(),
                view.studentFullName(),
                view.studentMatricule(),
                doc.getEnrollmentId(),
                doc.getGuardianId(),
                view.guardianFullName(),
                doc.getAcademicYearId(),
                view.academicYearCode(),
                view.academicYearLabel(),
                doc.getClassRoomId(),
                view.classRoomCode(),
                view.classRoomName(),
                doc.getTitle(),
                doc.getRenderedBody(),
                doc.getRenderedFooter(),
                doc.getContentType(),
                doc.getFileName(),
                doc.getFileSizeBytes(),
                doc.getContentSha256(),
                doc.getFeeAmountXof(),
                doc.getIssuedAt(),
                doc.getIssuedBy(),
                doc.getValidUntil(),
                doc.getRevokedAt(),
                doc.getRevokedBy(),
                doc.getRevocationReason());
    }
}
