package com.ecole.saas.center.api;

import com.ecole.saas.center.domain.CenterSessionPack;
import java.time.Instant;
import java.util.UUID;

public record CenterPackResponse(
        UUID id,
        UUID academicYearId,
        UUID gradeLevelId,
        UUID subjectId,
        String code,
        String name,
        String description,
        String billingMode,
        Integer includedSessions,
        Integer durationDays,
        long priceXof,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    static CenterPackResponse from(CenterSessionPack pack) {
        return new CenterPackResponse(
                pack.getId(),
                pack.getAcademicYearId(),
                pack.getGradeLevelId(),
                pack.getSubjectId(),
                pack.getCode(),
                pack.getName(),
                pack.getDescription(),
                pack.getBillingMode().name(),
                pack.getIncludedSessions(),
                pack.getDurationDays(),
                pack.getPriceXof(),
                pack.isActive(),
                pack.getCreatedAt(),
                pack.getUpdatedAt());
    }
}
