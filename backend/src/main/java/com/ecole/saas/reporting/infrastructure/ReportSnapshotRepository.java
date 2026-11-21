package com.ecole.saas.reporting.infrastructure;

import com.ecole.saas.reporting.domain.ReportSnapshot;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportSnapshotRepository
        extends JpaRepository<ReportSnapshot, UUID>, JpaSpecificationExecutor<ReportSnapshot> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ReportSnapshot s WHERE s.id = :id")
    Optional<ReportSnapshot> findByIdForUpdate(@Param("id") UUID id);

    Optional<ReportSnapshot> findByIdempotencyKey(String idempotencyKey);

    Optional<ReportSnapshot> findBySnapshotNumber(String snapshotNumber);

    @Query("SELECT COALESCE(SUM(s.fileSizeBytes), 0L) FROM ReportSnapshot s")
    long sumFileSizeBytes();
}
