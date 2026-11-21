package com.ecole.saas.schoolimport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Frontière publique Spring Modulith en lecture seule du module {@code schoolimport}
 * exposée aux modules aval (ex. {@code reporting}).
 */
public interface SchoolImportDirectoryPort {

    Optional<ImportJobSummary> findJobById(UUID jobId);

    Optional<ImportJobSummary> findJobByNumber(String jobNumber);

    List<ImportJobSummary> findRecentJobs(int limit);

    long countCompletedJobs();
}
