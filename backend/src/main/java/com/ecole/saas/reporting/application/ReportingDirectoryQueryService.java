package com.ecole.saas.reporting.application;

import com.ecole.saas.reporting.ReportSnapshotSummary;
import com.ecole.saas.reporting.ReportingDirectoryPort;
import com.ecole.saas.reporting.domain.ReportSnapshot;
import com.ecole.saas.reporting.infrastructure.ReportSnapshotRepository;
import com.ecole.saas.shared.domain.TenantContext;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation en lecture seule de la frontière publique {@link ReportingDirectoryPort}.
 */
@Service
public class ReportingDirectoryQueryService implements ReportingDirectoryPort {

    private final ReportSnapshotRepository snapshotRepository;

    public ReportingDirectoryQueryService(ReportSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReportSnapshotSummary> findSnapshotById(UUID snapshotId) {
        TenantContext.requireCurrent();
        if (snapshotId == null) {
            return Optional.empty();
        }
        return snapshotRepository.findById(snapshotId).map(ReportingDirectoryQueryService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReportSnapshotSummary> findSnapshotByNumber(String snapshotNumber) {
        TenantContext.requireCurrent();
        if (snapshotNumber == null || snapshotNumber.isBlank()) {
            return Optional.empty();
        }
        return snapshotRepository.findBySnapshotNumber(snapshotNumber.trim().toUpperCase(Locale.ROOT))
                .map(ReportingDirectoryQueryService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public long countSnapshotsForCurrentTenant() {
        TenantContext.requireCurrent();
        return snapshotRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long totalStorageBytesForCurrentTenant() {
        TenantContext.requireCurrent();
        return snapshotRepository.sumFileSizeBytes();
    }

    private static ReportSnapshotSummary toSummary(ReportSnapshot s) {
        return new ReportSnapshotSummary(
                s.getId(),
                s.getSnapshotNumber(),
                s.getReportType(),
                s.getOutputFormat(),
                s.getStatus(),
                s.getScopeType(),
                s.getAcademicYearId(),
                s.getEvaluationPeriodId(),
                s.getClassRoomId(),
                s.getStudentId(),
                s.getFromDate(),
                s.getToDate(),
                s.getTitle(),
                s.getFileName(),
                s.getFileSizeBytes(),
                s.getContentSha256(),
                s.getGeneratedBy(),
                s.getGeneratedAt(),
                s.getArchivedAt());
    }
}
