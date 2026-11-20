package com.ecole.saas.academic.api;

import com.ecole.saas.academic.domain.AcademicYear;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AcademicYearResponse(
        UUID id,
        String code,
        String label,
        LocalDate startDate,
        LocalDate endDate,
        String periodSystem,
        String status,
        boolean current,
        Instant closedAt,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static AcademicYearResponse from(AcademicYear year) {
        return new AcademicYearResponse(
                year.getId(),
                year.getCode(),
                year.getLabel(),
                year.getStartDate(),
                year.getEndDate(),
                year.getPeriodSystem().name(),
                year.getStatus().name(),
                year.isCurrent(),
                year.getClosedAt(),
                year.getArchivedAt(),
                year.getCreatedAt(),
                year.getUpdatedAt());
    }
}
