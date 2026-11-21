package com.ecole.saas.schooldocs.infrastructure;

import com.ecole.saas.schooldocs.domain.DocumentEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Dépôt strictement en ajout seul pour le journal des événements documentaires : aucune méthode {@code delete*}.
 */
public interface DocumentEventRepository extends Repository<DocumentEvent, UUID> {

    DocumentEvent save(DocumentEvent event);

    List<DocumentEvent> findByDocumentIdOrderBySequenceNoAsc(UUID documentId);

    @Query("SELECT COALESCE(MAX(e.sequenceNo), 0) FROM DocumentEvent e WHERE e.documentId = :documentId")
    int findMaxSequenceNoByDocumentId(@Param("documentId") UUID documentId);
}
