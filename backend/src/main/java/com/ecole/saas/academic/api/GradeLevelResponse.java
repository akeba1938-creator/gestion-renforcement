package com.ecole.saas.academic.api;

import com.ecole.saas.academic.domain.GradeLevel;
import java.time.Instant;
import java.util.UUID;

public record GradeLevelResponse(
        UUID id,
        String code,
        String name,
        String cycle,
        int displayOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static GradeLevelResponse from(GradeLevel level) {
        return new GradeLevelResponse(
                level.getId(),
                level.getCode(),
                level.getName(),
                level.getCycle().name(),
                level.getDisplayOrder(),
                level.isActive(),
                level.getCreatedAt(),
                level.getUpdatedAt());
    }
}
