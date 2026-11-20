package com.ecole.saas.school.api;

import com.ecole.saas.school.domain.School;
import java.time.Instant;
import java.util.UUID;

public record SchoolResponse(
        UUID id,
        String code,
        String name,
        String shortName,
        String schoolType,
        String status,
        String phone,
        String email,
        String address,
        String city,
        String region,
        String countryCode,
        String motto,
        String ministryCode,
        String website,
        String timezone,
        String currency,
        String gradingPeriodSystem,
        int gradingScale,
        String statusReason,
        Instant suspendedAt,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static SchoolResponse from(School school) {
        return new SchoolResponse(
                school.getId(),
                school.getCode(),
                school.getName(),
                school.getShortName(),
                school.getSchoolType().name(),
                school.getStatus().name(),
                school.getPhone(),
                school.getEmail(),
                school.getAddress(),
                school.getCity(),
                school.getRegion(),
                school.getCountryCode(),
                school.getMotto(),
                school.getMinistryCode(),
                school.getWebsite(),
                school.getTimezone(),
                school.getCurrency(),
                school.getGradingPeriodSystem().name(),
                school.getGradingScale(),
                school.getStatusReason(),
                school.getSuspendedAt(),
                school.getArchivedAt(),
                school.getCreatedAt(),
                school.getUpdatedAt());
    }
}
