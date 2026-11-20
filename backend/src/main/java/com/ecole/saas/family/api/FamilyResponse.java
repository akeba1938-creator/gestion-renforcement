package com.ecole.saas.family.api;

import com.ecole.saas.family.domain.Family;
import com.ecole.saas.family.domain.FamilyStatus;
import java.time.Instant;
import java.util.UUID;

public record FamilyResponse(
        UUID id,
        String code,
        String name,
        String primaryPhone,
        String email,
        String address,
        String city,
        String notes,
        FamilyStatus status,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static FamilyResponse from(Family family) {
        return new FamilyResponse(
                family.getId(),
                family.getCode(),
                family.getName(),
                family.getPrimaryPhone(),
                family.getEmail(),
                family.getAddress(),
                family.getCity(),
                family.getNotes(),
                family.getStatus(),
                family.getArchivedAt(),
                family.getCreatedAt(),
                family.getUpdatedAt());
    }
}
