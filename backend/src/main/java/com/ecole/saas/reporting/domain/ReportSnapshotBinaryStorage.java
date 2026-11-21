package com.ecole.saas.reporting.domain;

import com.ecole.saas.reporting.ReportFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrat pur de stockage binaire privé immuable pour les snapshots de rapports (PostgreSQL RLS / futur S3).
 */
public interface ReportSnapshotBinaryStorage {

    void store(
            UUID snapshotId,
            String storageObjectKey,
            String contentType,
            ReportFormat outputFormat,
            String sha256Hash,
            byte[] content);

    Optional<byte[]> load(UUID snapshotId, String expectedSha256);
}
