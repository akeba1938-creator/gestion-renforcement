package com.ecole.saas.student;

import java.util.UUID;

/**
 * Frontière publique Spring Modulith d'écriture contrôlée du module {@code student}
 * consommée unidirectionnellement par {@code schoolimport}.
 */
public interface StudentImportPort {

    StudentSummary createStudentForImport(ImportStudentCommand command);

    StudentSummary updateStudentForImport(UUID studentId, ImportStudentCommand command);

    StudentEnrollmentSummary enrollStudentForImport(ImportEnrollmentCommand command);

    StudentEnrollmentSummary transferClassForImport(UUID enrollmentId, UUID targetClassRoomId, String reason);
}
