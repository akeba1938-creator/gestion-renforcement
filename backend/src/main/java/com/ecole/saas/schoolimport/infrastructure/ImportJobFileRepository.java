package com.ecole.saas.schoolimport.infrastructure;

import com.ecole.saas.schoolimport.domain.ImportJobFile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Dépôt strictement en ajout seul pour les fichiers binaires importés : aucune méthode {@code delete*}.
 */
public interface ImportJobFileRepository extends Repository<ImportJobFile, UUID> {

    ImportJobFile save(ImportJobFile file);

    Optional<ImportJobFile> findByJobId(UUID jobId);

    @Query("SELECT COALESCE(SUM(f.fileSizeBytes), 0) FROM ImportJobFile f")
    long sumFileSizeBytes();
}
