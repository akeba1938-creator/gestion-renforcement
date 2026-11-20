package com.ecole.saas.center;

import java.util.UUID;

/**
 * Vue immuable d'un forfait / pack de séances en FCFA entiers.
 */
public record CenterSessionPackSummary(
        UUID id,
        UUID academicYearId,
        UUID gradeLevelId,
        UUID subjectId,
        String code,
        String name,
        String billingMode,
        Integer includedSessions,
        Integer durationDays,
        long priceXof,
        boolean active) {
}
