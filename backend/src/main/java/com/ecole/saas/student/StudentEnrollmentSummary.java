package com.ecole.saas.student;

import java.time.LocalDate;
import java.util.UUID;

public record StudentEnrollmentSummary(
        UUID id,
        UUID studentId,
        UUID academicYearId,
        UUID classRoomId,
        String enrollmentType,
        String status,
        LocalDate enrollmentDate) {
}
