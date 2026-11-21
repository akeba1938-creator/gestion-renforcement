package com.ecole.saas.schooldocs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Frontière publique Spring Modulith en lecture seule du module {@code schooldocs}
 * (ex. pour le module {@code reporting}) sans exposer les entités JPA ni les tables internes.
 */
public interface SchoolDocsDirectoryPort {

    Optional<IssuedDocumentSummary> findDocumentById(UUID documentId);

    Optional<IssuedDocumentSummary> findDocumentByNumber(String documentNumber);

    List<IssuedDocumentSummary> findDocumentsByStudentId(UUID studentId);

    long countDocumentsForCurrentTenant();

    long totalStorageBytesForCurrentTenant();
}
