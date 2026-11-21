package com.ecole.saas.schoolimport.infrastructure;

import com.ecole.saas.schoolimport.ImportSourceFormat;
import com.ecole.saas.schoolimport.domain.ImportFileBinaryStorage;
import com.ecole.saas.schoolimport.domain.ImportJobFile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adaptateur d'infrastructure stockant les fichiers d'import dans la table immuable
 * {@code schoolimport_job_files} sous RLS PostgreSQL fail-closed.
 */
@Component
public class DatabaseImportFileStorage implements ImportFileBinaryStorage {

    private final ImportJobFileRepository fileRepository;

    public DatabaseImportFileStorage(ImportJobFileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    @Override
    public void store(
            UUID jobId,
            String storageObjectKey,
            String contentType,
            ImportSourceFormat detectedFormat,
            String sha256Hash,
            byte[] content) {
        fileRepository.save(new ImportJobFile(
                jobId, storageObjectKey, contentType, detectedFormat, sha256Hash, content));
    }

    @Override
    public Optional<byte[]> load(UUID jobId) {
        return fileRepository.findByJobId(jobId).map(ImportJobFile::getContent);
    }
}
