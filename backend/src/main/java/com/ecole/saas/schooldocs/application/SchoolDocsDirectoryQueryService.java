package com.ecole.saas.schooldocs.application;

import com.ecole.saas.schooldocs.IssuedDocumentSummary;
import com.ecole.saas.schooldocs.SchoolDocsDirectoryPort;
import com.ecole.saas.schooldocs.domain.IssuedDocument;
import com.ecole.saas.schooldocs.infrastructure.IssuedDocumentRepository;
import com.ecole.saas.shared.domain.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation applicative en lecture seule de la frontière publique {@link SchoolDocsDirectoryPort}.
 */
@Service
public class SchoolDocsDirectoryQueryService implements SchoolDocsDirectoryPort {

    private final IssuedDocumentRepository documentRepository;
    private final Clock clock;

    public SchoolDocsDirectoryQueryService(IssuedDocumentRepository documentRepository, Clock clock) {
        this.documentRepository = documentRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IssuedDocumentSummary> findDocumentById(UUID documentId) {
        TenantContext.requireCurrent();
        if (documentId == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return documentRepository.findById(documentId).map(doc -> toSummary(doc, now));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IssuedDocumentSummary> findDocumentByNumber(String documentNumber) {
        TenantContext.requireCurrent();
        if (documentNumber == null || documentNumber.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return documentRepository.findByDocumentNumber(documentNumber.trim()).map(doc -> toSummary(doc, now));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssuedDocumentSummary> findDocumentsByStudentId(UUID studentId) {
        TenantContext.requireCurrent();
        if (studentId == null) {
            return List.of();
        }
        Instant now = clock.instant();
        return documentRepository.findByStudentIdOrderByIssuedAtDesc(studentId).stream()
                .map(doc -> toSummary(doc, now))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countDocumentsForCurrentTenant() {
        TenantContext.requireCurrent();
        return documentRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long totalStorageBytesForCurrentTenant() {
        TenantContext.requireCurrent();
        return documentRepository.sumFileSizeBytes();
    }

    private static IssuedDocumentSummary toSummary(IssuedDocument doc, Instant now) {
        return new IssuedDocumentSummary(
                doc.getId(),
                doc.getDocumentNumber(),
                doc.getDocumentType(),
                doc.getStatus(),
                doc.evaluateValidity(now),
                doc.getOriginalDocumentId(),
                doc.getEditionNumber(),
                doc.isDuplicate(),
                doc.getStudentId(),
                doc.getGuardianId(),
                doc.getAcademicYearId(),
                doc.getClassRoomId(),
                doc.getTitle(),
                doc.getFeeAmountXof(),
                doc.getFileSizeBytes(),
                doc.getContentSha256(),
                doc.getIssuedAt(),
                doc.getValidUntil(),
                doc.getRevokedAt());
    }
}
