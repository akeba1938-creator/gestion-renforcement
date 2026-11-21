package com.ecole.saas.reporting.application;

import com.ecole.saas.reporting.domain.ReportSecurityPolicy;
import com.ecole.saas.reporting.infrastructure.ReportSnapshotRepository;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.subscription.QuotaResource;
import com.ecole.saas.subscription.QuotaUsageProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remonte l'usage réel de stockage des snapshots de rapports en Mo ({@link QuotaResource#STORAGE_MB})
 * au module {@code subscription} via le SPI {@link QuotaUsageProvider}.
 */
@Component
public class ReportingStorageQuotaProvider implements QuotaUsageProvider {

    private final ReportSnapshotRepository snapshotRepository;

    public ReportingStorageQuotaProvider(ReportSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    @Override
    public QuotaResource resource() {
        return QuotaResource.STORAGE_MB;
    }

    @Override
    @Transactional(readOnly = true)
    public long currentUsageForCurrentTenant() {
        TenantContext.requireCurrent();
        return ReportSecurityPolicy.bytesToCeilingMiB(snapshotRepository.sumFileSizeBytes());
    }
}
