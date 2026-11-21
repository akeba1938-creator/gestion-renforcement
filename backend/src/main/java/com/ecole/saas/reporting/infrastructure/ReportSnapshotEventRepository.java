package com.ecole.saas.reporting.infrastructure;

import com.ecole.saas.reporting.domain.ReportSnapshotEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Dépôt strictement en ajout seul sur {@code reporting_snapshot_events} : aucune méthode de suppression.
 */
public interface ReportSnapshotEventRepository extends Repository<ReportSnapshotEvent, UUID> {

    ReportSnapshotEvent save(ReportSnapshotEvent event);

    List<ReportSnapshotEvent> findBySnapshotIdOrderBySequenceNoAsc(UUID snapshotId);

    @Query("SELECT COALESCE(MAX(e.sequenceNo), 0) FROM ReportSnapshotEvent e WHERE e.snapshotId = :snapshotId")
    int findMaxSequenceNoBySnapshotId(@Param("snapshotId") UUID snapshotId);
}
