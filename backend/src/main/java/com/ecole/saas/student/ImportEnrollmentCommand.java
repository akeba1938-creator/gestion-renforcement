package com.ecole.saas.student;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Commande publique immuable d'import d'une inscription annuelle élève dans une classe.
 */
public record ImportEnrollmentCommand(
        UUID studentId,
        UUID academicYearId,
        UUID classRoomId,
        String enrollmentType,
        LocalDate enrollmentDate,
        String previousSchool,
        Boolean repeating,
        String notes) {
}
