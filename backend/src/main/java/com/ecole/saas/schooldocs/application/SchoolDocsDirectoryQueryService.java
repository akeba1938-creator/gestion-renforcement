package com.ecole.saas.schooldocs.application;

import com.ecole.saas.schooldocs.DocumentStatus;
import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schooldocs.IssuedDocumentSummary;
import com.ecole.saas.schooldocs.SchoolDocsActivitySummary;
import com.ecole.saas.schooldocs.SchoolDocsDirectoryPort;
import com.ecole.saas.schooldocs.domain.IssuedDocument;
import com.ecole.saas.schooldocs.infrastructure.IssuedDocumentRepository;
import com.ecole.saas.shared.domain.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation applicative en lecture seule de la frontière publique {@link SchoolDocsDirectoryPort}.
 */
@Service
public class SchoolDocsDirectoryQueryService implements SchoolDocsDirectoryPort {

    private static final Instant MIN_INSTANT = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX_INSTANT = Instant.parse("2100-01-01T00:00:00Z");

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
    public long countIssuedDocumentsByStudentId(UUID studentId) {
        TenantContext.requireCurrent();
        if (studentId == null) {
            return 0L;
        }
        return documentRepository.countByStudentIdAndStatus(studentId, DocumentStatus.ISSUED);
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

    @Override
    @Transactional(readOnly = true)
    public SchoolDocsActivitySummary getActivitySummary(
            UUID academicYearId, Instant fromInclusive, Instant toExclusive) {
        TenantContext.requireCurrent();
        Instant now = clock.instant();
        Instant start = fromInclusive != null ? fromInclusive : MIN_INSTANT;
        Instant end = toExclusive != null ? toExclusive : MAX_INSTANT;

        List<Object[]> totals = academicYearId != null
                ? documentRepository.aggregateSummaryTotalsForYearAndRange(now, academicYearId, start, end)
                : documentRepository.aggregateSummaryTotalsForRange(now, start, end);
        List<Object[]> grouped = academicYearId != null
                ? documentRepository.countGroupedByDocumentTypeForYearAndRange(academicYearId, start, end)
                : documentRepository.countGroupedByDocumentTypeForRange(start, end);

        long total = 0L;
        long issued = 0L;
        long revoked = 0L;
        long expired = 0L;
        long duplicates = 0L;
        long storageBytes = 0L;
        long feeAmountXof = 0L;
        if (!totals.isEmpty() && totals.getFirst() != null) {
            Object[] row = totals.getFirst();
            total = ((Number) row[0]).longValue();
            issued = ((Number) row[1]).longValue();
            revoked = ((Number) row[2]).longValue();
            expired = ((Number) row[3]).longValue();
            duplicates = ((Number) row[4]).longValue();
            storageBytes = ((Number) row[5]).longValue();
            feeAmountXof = ((Number) row[6]).longValue();
        }

        Map<DocumentType, Long> byType = new EnumMap<>(DocumentType.class);
        for (Object[] row : grouped) {
            if (row != null && row[0] instanceof DocumentType dt && row[1] instanceof Number cnt) {
                byType.put(dt, cnt.longValue());
            }
        }

        return new SchoolDocsActivitySummary(
                total,
                issued,
                revoked,
                expired,
                duplicates,
                feeAmountXof,
                storageBytes,
                Collections.unmodifiableMap(byType));
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
