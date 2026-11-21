package com.ecole.saas.grade;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Frontière publique Spring Modulith d'import de notes du module {@code grade}
 * consommée unidirectionnellement par {@code schoolimport}.
 */
public interface GradeImportPort {

    Optional<EvaluationImportContext> findEvaluationImportContext(UUID evaluationId);

    int upsertGradesForImport(UUID evaluationId, List<ImportGradeEntryCommand> entries, UUID actorId);
}
