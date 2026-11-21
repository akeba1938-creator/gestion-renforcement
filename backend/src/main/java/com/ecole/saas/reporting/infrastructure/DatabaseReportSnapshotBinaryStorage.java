package com.ecole.saas.reporting.infrastructure;

import com.ecole.saas.reporting.ReportFormat;
import com.ecole.saas.reporting.domain.ReportSecurityPolicy;
import com.ecole.saas.reporting.domain.ReportSnapshotBinaryStorage;
import com.ecole.saas.reporting.domain.ReportSnapshotContent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implémentation PostgreSQL sous RLS fail-closed du stockage binaire privé immuable des snapshots.
 */
@Component
public class DatabaseReportSnapshotBinaryStorage implements ReportSnapshotBinaryStorage {

    private final ReportSnapshotContentRepository contentRepository;

    public DatabaseReportSnapshotBinaryStorage(ReportSnapshotContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    @Override
    public void store(
            UUID snapshotId,
            String storageObjectKey,
            String contentType,
            ReportFormat outputFormat,
            String sha256Hash,
            byte[] content) {
        ReportSecurityPolicy.assertIntegrity(content, sha256Hash);
        ReportSnapshotContent entity = new ReportSnapshotContent(
                snapshotId,
                storageObjectKey,
                contentType,
                outputFormat,
                sha256Hash,
                content);
        contentRepository.save(entity);
    }

    @Override
    public Optional<byte[]> load(UUID snapshotId, String expectedSha256) {
        return contentRepository.findBySnapshotId(snapshotId).map(entity -> {
            byte[] bytes = entity.getContentCopy();
            ReportSecurityPolicy.assertIntegrity(bytes, expectedSha256);
            return bytes;
        });
    }
}
