package com.ecole.saas.schooldocs.infrastructure;

import com.ecole.saas.schooldocs.domain.DocumentContent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Dépôt strictement en ajout seul pour les contenus binaires PDF immuables : aucune méthode {@code delete*}.
 */
public interface DocumentContentRepository extends Repository<DocumentContent, UUID> {

    DocumentContent save(DocumentContent content);

    Optional<DocumentContent> findByDocumentId(UUID documentId);
}
