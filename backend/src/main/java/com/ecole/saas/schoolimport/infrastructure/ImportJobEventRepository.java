package com.ecole.saas.schoolimport.infrastructure;

import com.ecole.saas.schoolimport.domain.ImportJobEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Dépôt strictement en ajout seul pour le journal des événements de lots d'import : aucune méthode {@code delete*}.
 */
public interface ImportJobEventRepository extends Repository<ImportJobEvent, UUID> {

    ImportJobEvent save(ImportJobEvent event);

    List<ImportJobEvent> findByJobIdOrderBySequenceNoAsc(UUID jobId);

    @Query("SELECT COALESCE(MAX(e.sequenceNo), 0) FROM ImportJobEvent e WHERE e.jobId = :jobId")
    int findMaxSequenceNoByJobId(@Param("jobId") UUID jobId);
}
