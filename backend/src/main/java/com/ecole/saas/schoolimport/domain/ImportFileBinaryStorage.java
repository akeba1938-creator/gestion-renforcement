package com.ecole.saas.schoolimport.domain;

import com.ecole.saas.schoolimport.ImportSourceFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrat domaine pur de stockage binaire privé immuable des fichiers téléversés.
 */
public interface ImportFileBinaryStorage {

    void store(
            UUID jobId,
            String storageObjectKey,
            String contentType,
            ImportSourceFormat detectedFormat,
            String sha256Hash,
            byte[] content);

    Optional<byte[]> load(UUID jobId);
}
