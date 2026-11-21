package com.ecole.saas.attendance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Frontière publique Spring Modulith d'import de présences du module {@code attendance}
 * consommée unidirectionnellement par {@code schoolimport}.
 */
public interface AttendanceImportPort {

    Optional<AttendanceSessionImportContext> findSessionImportContext(UUID sessionId);

    int upsertAttendanceForImport(UUID sessionId, List<ImportAttendanceEntryCommand> entries, UUID actorId);
}
