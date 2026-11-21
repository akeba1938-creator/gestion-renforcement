package com.ecole.saas.schooldocs.application;

import com.ecole.saas.schooldocs.domain.DocumentSecurityPolicy;
import com.ecole.saas.schooldocs.infrastructure.IssuedDocumentRepository;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.subscription.QuotaResource;
import com.ecole.saas.subscription.QuotaUsageProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remonte l'usage réel de stockage documentaire en Mo ({@link QuotaResource#STORAGE_MB})
 * au module {@code subscription} via le SPI {@link QuotaUsageProvider}.
 */
@Component
public class SchoolDocsStorageQuotaProvider implements QuotaUsageProvider {

    private final IssuedDocumentRepository issuedDocumentRepository;

    public SchoolDocsStorageQuotaProvider(IssuedDocumentRepository issuedDocumentRepository) {
        this.issuedDocumentRepository = issuedDocumentRepository;
    }

    @Override
    public QuotaResource resource() {
        return QuotaResource.STORAGE_MB;
    }

    @Override
    @Transactional(readOnly = true)
    public long currentUsageForCurrentTenant() {
        TenantContext.requireCurrent();
        return DocumentSecurityPolicy.bytesToCeilingMiB(issuedDocumentRepository.sumFileSizeBytes());
    }
}
