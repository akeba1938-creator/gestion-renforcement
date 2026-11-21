package com.ecole.saas.schooldocs.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Contrat de domaine pur abstrait pour le stockage binaire privé isolé par tenant.
 * Permet un remplacement transparent vers un stockage S3 privé sans toucher au domaine ni aux services.
 */
public interface DocumentBinaryStorage {

    void store(UUID documentId, String storageObjectKey, String contentType, String sha256Hash, byte[] content);

    Optional<StoredBinary> load(UUID documentId);

    record StoredBinary(
            UUID documentId,
            String storageObjectKey,
            String contentType,
            long fileSizeBytes,
            String sha256Hash,
            byte[] content) {
    }
}
