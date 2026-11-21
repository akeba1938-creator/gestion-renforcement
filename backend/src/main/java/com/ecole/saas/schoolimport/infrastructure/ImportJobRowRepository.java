package com.ecole.saas.schoolimport.infrastructure;

import com.ecole.saas.schoolimport.ImportRowAction;
import com.ecole.saas.schoolimport.ImportRowStatus;
import com.ecole.saas.schoolimport.domain.ImportJobRow;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ImportJobRowRepository
        extends JpaRepository<ImportJobRow, UUID>, JpaSpecificationExecutor<ImportJobRow> {

    List<ImportJobRow> findByJobIdOrderByRowNumberAsc(UUID jobId);

    List<ImportJobRow> findByJobIdAndRowStatusOrderByRowNumberAsc(UUID jobId, ImportRowStatus rowStatus);

    Page<ImportJobRow> findByJobId(UUID jobId, Pageable pageable);

    Page<ImportJobRow> findByJobIdAndRowStatus(UUID jobId, ImportRowStatus rowStatus, Pageable pageable);

    Page<ImportJobRow> findByJobIdAndPlannedAction(UUID jobId, ImportRowAction plannedAction, Pageable pageable);

    Page<ImportJobRow> findByJobIdAndRowStatusAndPlannedAction(
            UUID jobId, ImportRowStatus rowStatus, ImportRowAction plannedAction, Pageable pageable);
}
