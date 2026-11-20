package com.ecole.saas.academic.api;

import com.ecole.saas.academic.domain.Subject;
import java.time.Instant;
import java.util.UUID;

public record SubjectResponse(
        UUID id,
        String code,
        String name,
        String shortName,
        String category,
        int defaultCoefficient,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static SubjectResponse from(Subject subject) {
        return new SubjectResponse(
                subject.getId(),
                subject.getCode(),
                subject.getName(),
                subject.getShortName(),
                subject.getCategory().name(),
                subject.getDefaultCoefficient(),
                subject.isActive(),
                subject.getCreatedAt(),
                subject.getUpdatedAt());
    }
}
