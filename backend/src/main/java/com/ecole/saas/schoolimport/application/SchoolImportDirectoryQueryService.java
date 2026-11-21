package com.ecole.saas.schoolimport.application;

import com.ecole.saas.schoolimport.ImportJobStatus;
import com.ecole.saas.schoolimport.ImportJobSummary;
import com.ecole.saas.schoolimport.SchoolImportDirectoryPort;
import com.ecole.saas.schoolimport.domain.ImportJob;
import com.ecole.saas.schoolimport.infrastructure.ImportJobRepository;
import com.ecole.saas.shared.domain.TenantContext;
import java.util.List;
import java.util.Locale;
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
