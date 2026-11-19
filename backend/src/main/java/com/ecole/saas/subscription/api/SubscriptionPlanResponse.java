package com.ecole.saas.subscription.api;

import com.ecole.saas.subscription.domain.SubscriptionPlan;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionPlanResponse(
        UUID id,
        String code,
        String name,
        String description,
        long monthlyPriceXof,
        long yearlyPriceXof,
        int maxStudents,
        int maxUsers,
        int maxStorageMb,
        int trialDays,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static SubscriptionPlanResponse from(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getMonthlyPriceXof(),
                plan.getYearlyPriceXof(),
                plan.getMaxStudents(),
                plan.getMaxUsers(),
                plan.getMaxStorageMb(),
                plan.getTrialDays(),
                plan.isActive(),
                plan.getCreatedAt(),
                plan.getUpdatedAt());
    }
}
