package com.ecole.saas.center;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Vue immuable d'un groupe / cohorte de renforcement exposée aux autres modules applicatifs.
 */
public record CenterGroupSummary(
        UUID id,
        UUID academicYearId,
        UUID gradeLevelId,
        UUID subjectId,
        UUID defaultPackId,
        String code,
        String name,
        String groupType,
        String roomName,
        int capacity,
        long activeEnrollmentCount,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        boolean active) {
}
