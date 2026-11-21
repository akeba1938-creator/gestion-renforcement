package com.ecole.saas.schoolimport.application;

import com.ecole.saas.schoolimport.ImportJobStatus;
import com.ecole.saas.schoolimport.ImportJobSummary;
import com.ecole.saas.schoolimport.ImportType;
import com.ecole.saas.schoolimport.SchoolImportActivitySummary;
import com.ecole.saas.schoolimport.SchoolImportDirectoryPort;
import com.ecole.saas.schoolimport.domain.ImportJob;
import com.ecole.saas.schoolimport.infrastructure.ImportJobRepository;
import com.ecole.saas.shared.domain.TenantContext;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation en lecture seule de {@link SchoolImportDirectoryPort} dans le contexte tenant courant.
 */
@Service
public class SchoolImportDirectoryQueryService implements SchoolImportDirectoryPort {

    private static final Instant MIN_INSTANT = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX_INSTANT = Instant.parse("2100-01-01T00:00:00Z");

    private final ImportJobRepository jobRepository;

    public SchoolImportDirectoryQueryService(ImportJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ImportJobSummary> findJobById(UUID jobId) {
        TenantContext.requireCurrent();
        if (jobId == null) {
            return Optional.empty();
        }
        return jobRepository.findById(jobId).map(SchoolImportDirectoryQueryService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ImportJobSummary> findJobByNumber(String jobNumber) {
        TenantContext.requireCurrent();
        if (jobNumber == null || jobNumber.isBlank()) {
            return Optional.empty();
        }
        return jobRepository.findByJobNumber(jobNumber.trim().toUpperCase(Locale.ROOT))
                .map(SchoolImportDirectoryQueryService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImportJobSummary> findRecentJobs(int limit) {
        TenantContext.requireCurrent();
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jobRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit)).stream()
                .map(SchoolImportDirectoryQueryService::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countCompletedJobs() {
        TenantContext.requireCurrent();
        return jobRepository.countByStatusIn(
                List.of(ImportJobStatus.COMPLETED, ImportJobStatus.COMPLETED_WITH_ERRORS));
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolImportActivitySummary getActivitySummary(
            UUID academicYearId, Instant fromInclusive, Instant toExclusive) {
        TenantContext.requireCurrent();
        Instant start = fromInclusive != null ? fromInclusive : MIN_INSTANT;
        Instant end = toExclusive != null ? toExclusive : MAX_INSTANT;

        List<Object[]> totals = academicYearId != null
                ? jobRepository.aggregateSummaryTotalsForYearAndRange(academicYearId, start, end)
                : jobRepository.aggregateSummaryTotalsForRange(start, end);
        List<Object[]> grouped = academicYearId != null
                ? jobRepository.countGroupedByImportTypeForYearAndRange(academicYearId, start, end)
                : jobRepository.countGroupedByImportTypeForRange(start, end);

        long totalJobs = 0L;
        long previewReady = 0L;
        long validationFailed = 0L;
        long completed = 0L;
        long completedWithErrors = 0L;
        long failed = 0L;
        long cancelled = 0L;
        long totalRows = 0L;
        long createdRows = 0L;
        long updatedRows = 0L;
        long skippedRows = 0L;
        long errorRows = 0L;
        long storageBytes = 0L;
        if (!totals.isEmpty() && totals.getFirst() != null) {
            Object[] row = totals.getFirst();
            totalJobs = ((Number) row[0]).longValue();
            previewReady = ((Number) row[1]).longValue();
            validationFailed = ((Number) row[2]).longValue();
            completed = ((Number) row[3]).longValue();
            completedWithErrors = ((Number) row[4]).longValue();
            failed = ((Number) row[5]).longValue();
            cancelled = ((Number) row[6]).longValue();
            totalRows = ((Number) row[7]).longValue();
            createdRows = ((Number) row[8]).longValue();
            updatedRows = ((Number) row[9]).longValue();
            skippedRows = ((Number) row[10]).longValue();
            errorRows = ((Number) row[11]).longValue();
            storageBytes = ((Number) row[12]).longValue();
        }

        Map<ImportType, Long> byType = new EnumMap<>(ImportType.class);
        for (Object[] row : grouped) {
            if (row != null && row[0] instanceof ImportType type && row[1] instanceof Number cnt) {
                byType.put(type, cnt.longValue());
            }
        }

        return new SchoolImportActivitySummary(
                totalJobs,
                previewReady,
                validationFailed,
                completed,
                completedWithErrors,
                failed,
                cancelled,
                totalRows,
                createdRows,
                updatedRows,
                skippedRows,
                errorRows,
                storageBytes,
                Collections.unmodifiableMap(byType));
    }

    static ImportJobSummary toSummary(ImportJob job) {
        return new ImportJobSummary(
                job.getId(),
                job.getJobNumber(),
                job.getImportType(),
                job.getSourceFormat(),
                job.getValidationMode(),
                job.getDuplicateStrategy(),
                job.getStatus(),
                job.getFileName(),
                job.getFileSizeBytes(),
                job.getTotalRows(),
                job.getValidRows(),
                job.getErrorRows(),
                job.getDuplicateRows(),
                job.getCreatedCount(),
                job.getUpdatedCount(),
                job.getSkippedCount(),
                job.getCreatedBy(),
                job.getAnalyzedAt(),
                job.getExecutedAt());
    }
}
