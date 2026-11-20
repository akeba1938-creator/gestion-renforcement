package com.ecole.saas.center;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Vue immuable d'une inscription d'élève à une cohorte de renforcement.
 */
public record CenterGroupEnrollmentSummary(
        UUID id,
        UUID groupId,
        String groupCode,
        String groupName,
        UUID studentId,
        UUID studentPackId,
        LocalDate enrolledAt,
        LocalDate endedAt,
        String status) {
}
