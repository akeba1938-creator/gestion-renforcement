package com.ecole.saas.schoolimport.infrastructure;

import com.ecole.saas.schoolimport.ImportJobStatus;
import com.ecole.saas.schoolimport.domain.ImportJob;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImportJobRepository
        extends JpaRepository<ImportJob, UUID>, JpaSpecificationExecutor<ImportJob> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM ImportJob j WHERE j.id = :id")
    Optional<ImportJob> findByIdForUpdate(@Param("id") UUID id);

    Optional<ImportJob> findByIdempotencyKey(String idempotencyKey);

    Optional<ImportJob> findByExecutionIdempotencyKey(String executionIdempotencyKey);

    Optional<ImportJob> findByJobNumber(String jobNumber);

    List<ImportJob> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatusIn(Collection<ImportJobStatus> statuses);

    @Query("""
            SELECT
                COUNT(j),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.PREVIEW_READY THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.VALIDATION_FAILED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.COMPLETED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.COMPLETED_WITH_ERRORS THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.FAILED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.CANCELLED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(j.totalRows), 0L),
                COALESCE(SUM(j.createdCount), 0L),
                COALESCE(SUM(j.updatedCount), 0L),
                COALESCE(SUM(j.skippedCount), 0L),
                COALESCE(SUM(j.errorRows), 0L),
                COALESCE(SUM(j.fileSizeBytes), 0L)
            FROM ImportJob j
            """)
    List<Object[]> aggregateSummaryTotals();

    @Query("""
            SELECT j.importType, COUNT(j)
            FROM ImportJob j
            GROUP BY j.importType
            """)
    List<Object[]> countGroupedByImportType();

    @Query("""
            SELECT
                COUNT(j),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.PREVIEW_READY THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.VALIDATION_FAILED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.COMPLETED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.COMPLETED_WITH_ERRORS THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.FAILED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.CANCELLED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(j.totalRows), 0L),
                COALESCE(SUM(j.createdCount), 0L),
                COALESCE(SUM(j.updatedCount), 0L),
                COALESCE(SUM(j.skippedCount), 0L),
                COALESCE(SUM(j.errorRows), 0L),
                COALESCE(SUM(j.fileSizeBytes), 0L)
            FROM ImportJob j
            WHERE j.analyzedAt >= :fromInclusive
              AND j.analyzedAt < :toExclusive
            """)
    List<Object[]> aggregateSummaryTotalsForRange(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @Query("""
            SELECT
                COUNT(j),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.PREVIEW_READY THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.VALIDATION_FAILED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.COMPLETED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.COMPLETED_WITH_ERRORS THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.FAILED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN j.status = com.ecole.saas.schoolimport.ImportJobStatus.CANCELLED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(j.totalRows), 0L),
                COALESCE(SUM(j.createdCount), 0L),
                COALESCE(SUM(j.updatedCount), 0L),
                COALESCE(SUM(j.skippedCount), 0L),
                COALESCE(SUM(j.errorRows), 0L),
                COALESCE(SUM(j.fileSizeBytes), 0L)
            FROM ImportJob j
            WHERE j.academicYearId = :academicYearId
              AND j.analyzedAt >= :fromInclusive
              AND j.analyzedAt < :toExclusive
            """)
    List<Object[]> aggregateSummaryTotalsForYearAndRange(
            @Param("academicYearId") UUID academicYearId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @Query("""
            SELECT j.importType, COUNT(j)
            FROM ImportJob j
            WHERE j.analyzedAt >= :fromInclusive
              AND j.analyzedAt < :toExclusive
            GROUP BY j.importType
            """)
    List<Object[]> countGroupedByImportTypeForRange(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    @Query("""
            SELECT j.importType, COUNT(j)
            FROM ImportJob j
            WHERE j.academicYearId = :academicYearId
              AND j.analyzedAt >= :fromInclusive
              AND j.analyzedAt < :toExclusive
            GROUP BY j.importType
            """)
    List<Object[]> countGroupedByImportTypeForYearAndRange(
            @Param("academicYearId") UUID academicYearId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);
}
