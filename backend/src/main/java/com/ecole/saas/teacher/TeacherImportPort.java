package com.ecole.saas.teacher;

import java.util.UUID;

/**
 * Frontière publique Spring Modulith d'écriture contrôlée du module {@code teacher}
 * consommée unidirectionnellement par {@code schoolimport}.
 */
public interface TeacherImportPort {

    TeacherSummary createTeacherForImport(ImportTeacherCommand command);

    TeacherSummary updateTeacherForImport(UUID teacherId, ImportTeacherCommand command);
}
