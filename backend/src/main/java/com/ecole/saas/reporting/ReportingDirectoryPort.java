package com.ecole.saas.reporting;

import java.util.Optional;
import java.util.UUID;

/**
 * Frontière publique Spring Modulith en lecture seule du module {@code reporting}.
 */
public interface ReportingDirectoryPort {

    Optional<ReportSnapshotSummary> findSnapshotById(UUID snapshotId);

    Optional<ReportSnapshotSummary> findSnapshotByNumber(String snapshotNumber);

    long countSnapshotsForCurrentTenant();

    long totalStorageBytesForCurrentTenant();
}
