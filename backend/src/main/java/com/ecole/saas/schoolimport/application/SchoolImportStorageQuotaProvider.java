package com.ecole.saas.schoolimport.application;

import com.ecole.saas.schoolimport.domain.ImportFileSecurityPolicy;
import com.ecole.saas.schoolimport.infrastructure.ImportJobFileRepository;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.subscription.QuotaResource;
import com.ecole.saas.subscription.QuotaUsageProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remonte l'usage réel de stockage des fichiers d'import en Mo ({@link QuotaResource#STORAGE_MB})
 * au module {@code subscription} via le SPI {@link QuotaUsageProvider}.
 */
@Component
public class SchoolImportStorageQuotaProvider implements QuotaUsageProvider {

    private final ImportJobFileRepository fileRepository;

    public SchoolImportStorageQuotaProvider(ImportJobFileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    @Override
    public QuotaResource resource() {
        return QuotaResource.STORAGE_MB;
    }

    @Override
    @Transactional(readOnly = true)
    public long currentUsageForCurrentTenant() {
        TenantContext.requireCurrent();
        return ImportFileSecurityPolicy.bytesToCeilingMiB(fileRepository.sumFileSizeBytes());
    }
}
