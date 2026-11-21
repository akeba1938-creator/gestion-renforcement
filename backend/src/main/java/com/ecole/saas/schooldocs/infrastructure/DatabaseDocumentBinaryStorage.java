package com.ecole.saas.schooldocs.infrastructure;

import com.ecole.saas.schooldocs.domain.DocumentBinaryStorage;
import com.ecole.saas.schooldocs.domain.DocumentContent;
import com.ecole.saas.schooldocs.domain.DocumentSecurityPolicy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implémentation par défaut du stockage binaire privé isolé par tenant dans PostgreSQL (RLS forcée + ajout seul).
 * Vérifie systématiquement le type réel PDF ({@code %PDF-}) et l'empreinte SHA-256 à l'écriture comme à la lecture.
 */
@Component
public class DatabaseDocumentBinaryStorage implements DocumentBinaryStorage {

    private final DocumentContentRepository contentRepository;

    public DatabaseDocumentBinaryStorage(DocumentContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    @Override
    public void store(
            UUID documentId,
            String storageObjectKey,
            String contentType,
            String sha256Hash,
            byte[] content) {
        DocumentSecurityPolicy.assertIntegrity(content, sha256Hash);
        DocumentContent entity = new DocumentContent(documentId, storageObjectKey, contentType, sha256Hash, content);
        contentRepository.save(entity);
    }

    @Override
    public Optional<StoredBinary> load(UUID documentId) {
        return contentRepository.findByDocumentId(documentId).map(entity -> {
            byte[] bytes = entity.getContent();
            DocumentSecurityPolicy.assertIntegrity(bytes, entity.getSha256Hash());
            return new StoredBinary(
                    entity.getDocumentId(),
                    entity.getStorageObjectKey(),
                    entity.getContentType(),
                    entity.getFileSizeBytes(),
                    entity.getSha256Hash(),
                    bytes);
        });
    }
}
