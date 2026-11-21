package com.ecole.saas.schooldocs.infrastructure;

import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schooldocs.domain.IssuedDocument;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssuedDocumentRepository
        extends JpaRepository<IssuedDocument, UUID>, JpaSpecificationExecutor<IssuedDocument> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM IssuedDocument d WHERE d.id = :id")
    Optional<IssuedDocument> findByIdForUpdate(@Param("id") UUID id);

    Optional<IssuedDocument> findByIdempotencyKey(String idempotencyKey);

    Optional<IssuedDocument> findByDocumentNumber(String documentNumber);

    Optional<IssuedDocument> findByVerificationToken(String verificationToken);

    List<IssuedDocument> findByOriginalDocumentIdOrderByEditionNumberAsc(UUID originalDocumentId);

    List<IssuedDocument> findByStudentIdOrderByIssuedAtDesc(UUID studentId);

    @Query("""
            SELECT COALESCE(MAX(d.editionNumber), 1)
            FROM IssuedDocument d
            WHERE d.id = :originalId OR d.originalDocumentId = :originalId
            """)
    int findMaxEditionNumberForRoot(@Param("originalId") UUID originalId);

    @Query("SELECT COALESCE(SUM(d.fileSizeBytes), 0) FROM IssuedDocument d")
    long sumFileSizeBytes();

    @Query("""
            SELECT
                COUNT(d),
                COALESCE(SUM(CASE WHEN d.status = com.ecole.saas.schooldocs.DocumentStatus.ISSUED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN d.status = com.ecole.saas.schooldocs.DocumentStatus.REVOKED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN d.status = com.ecole.saas.schooldocs.DocumentStatus.ISSUED
                    AND d.validUntil IS NOT NULL AND d.validUntil < :now THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN d.duplicate = true THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(d.fileSizeBytes), 0L),
                COALESCE(SUM(d.feeAmountXof), 0L)
            FROM IssuedDocument d
            """)
    List<Object[]> aggregateSummaryTotals(@Param("now") Instant now);

    @Query("""
            SELECT d.documentType, COUNT(d)
            FROM IssuedDocument d
            GROUP BY d.documentType
            """)
    List<Object[]> countGroupedByDocumentType();

    @Query("""
            SELECT
                COUNT(d),
                COALESCE(SUM(CASE WHEN d.status = com.ecole.saas.schooldocs.DocumentStatus.ISSUED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN d.status = com.ecole.saas.schooldocs.DocumentStatus.REVOKED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN d.status = com.ecole.saas.schooldocs.DocumentStatus.ISSUED
                    AND d.validUntil IS NOT NULL AND d.validUntil < :now THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN d.duplicate = true THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(d.fileSizeBytes), 0L),
                COALESCE(SUM(d.feeAmountXof), 0L)
            FROM IssuedDocument d
            WHERE d.issuedAt >= :fromInclusive
              AND d.issuedAt < :toExclusive
            """)
    List<Object[]> aggregateSummaryTotalsForRange(
            @Param("now") Instant now,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @Query("""
            SELECT
                COUNT(d),
                COALESCE(SUM(CASE WHEN d.status = com.ecole.saas.schooldocs.DocumentStatus.ISSUED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN d.status = com.ecole.saas.schooldocs.DocumentStatus.REVOKED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN d.status = com.ecole.saas.schooldocs.DocumentStatus.ISSUED
                    AND d.validUntil IS NOT NULL AND d.validUntil < :now THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN d.duplicate = true THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(d.fileSizeBytes), 0L),
                COALESCE(SUM(d.feeAmountXof), 0L)
            FROM IssuedDocument d
            WHERE d.academicYearId = :academicYearId
              AND d.issuedAt >= :fromInclusive
              AND d.issuedAt < :toExclusive
            """)
    List<Object[]> aggregateSummaryTotalsForYearAndRange(
            @Param("now") Instant now,
            @Param("academicYearId") UUID academicYearId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @Query("""
            SELECT d.documentType, COUNT(d)
            FROM IssuedDocument d
            WHERE d.issuedAt >= :fromInclusive
              AND d.issuedAt < :toExclusive
            GROUP BY d.documentType
            """)
    List<Object[]> countGroupedByDocumentTypeForRange(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @Query("""
            SELECT d.documentType, COUNT(d)
            FROM IssuedDocument d
            WHERE d.academicYearId = :academicYearId
              AND d.issuedAt >= :fromInclusive
              AND d.issuedAt < :toExclusive
            GROUP BY d.documentType
            """)
    List<Object[]> countGroupedByDocumentTypeForYearAndRange(
            @Param("academicYearId") UUID academicYearId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);
}
