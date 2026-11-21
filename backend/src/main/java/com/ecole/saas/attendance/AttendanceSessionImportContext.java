package com.ecole.saas.attendance;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Vue publique immuable d'une séance d'appel cible pour la validation à sec et l'import de présences.
 */
public record AttendanceSessionImportContext(
        UUID sessionId,
        UUID academicYearId,
        UUID evaluationPeriodId,
        String periodStatus,
        UUID classRoomId,
        UUID subjectId,
        LocalDate sessionDate,
        String status,
        Set<UUID> existingRecordedStudentIds) {
}
