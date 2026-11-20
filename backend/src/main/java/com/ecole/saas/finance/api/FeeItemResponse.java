package com.ecole.saas.finance.api;

import com.ecole.saas.finance.domain.FeeCategory;
import com.ecole.saas.finance.domain.FeeFrequency;
import com.ecole.saas.finance.domain.FeeItem;
import java.time.Instant;
import java.util.UUID;

public record FeeItemResponse(
        UUID id,
        UUID academicYearId,
        UUID gradeLevelId,
        String code,
        String label,
        FeeCategory category,
        FeeFrequency frequency,
        long amountXof,
        boolean mandatory,
        boolean active,
        String description,
        Instant createdAt,
        Instant updatedAt) {

    public static FeeItemResponse from(FeeItem item) {
        return new FeeItemResponse(
                item.getId(),
                item.getAcademicYearId(),
                item.getGradeLevelId(),
                item.getCode(),
                item.getLabel(),
                item.getCategory(),
                item.getFrequency(),
                item.getAmountXof(),
                item.isMandatory(),
                item.isActive(),
                item.getDescription(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
