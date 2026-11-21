package com.ecole.saas.reporting.infrastructure;

import com.ecole.saas.reporting.domain.ReportSnapshotContent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Dépôt strictement en ajout seul sur {@code reporting_snapshot_contents} : aucune méthode de suppression.
 */
public interface ReportSnapshotContentRepository extends Repository<ReportSnapshotContent, UUID> {

    ReportSnapshotContent save(ReportSnapshotContent content);

    Optional<ReportSnapshotContent> findBySnapshotId(UUID snapshotId);
}
