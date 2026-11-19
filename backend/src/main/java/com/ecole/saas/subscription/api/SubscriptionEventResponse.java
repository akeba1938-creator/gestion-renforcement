package com.ecole.saas.subscription.api;

import com.ecole.saas.subscription.domain.SubscriptionEvent;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionEventResponse(
        UUID id,
        UUID subscriptionId,
        int sequenceNo,
        String eventType,
        String previousStatus,
        String newStatus,
        UUID planId,
        long amountXof,
        int effectiveMaxStudents,
        int effectiveMaxUsers,
        int effectiveMaxStorageMb,
        String reason,
        UUID actorId,
        Instant occurredAt) {

    public static SubscriptionEventResponse from(SubscriptionEvent event) {
        return new SubscriptionEventResponse(
                event.getId(),
                event.getSubscriptionId(),
                event.getSequenceNo(),
                event.getEventType().name(),
                event.getPreviousStatus() != null ? event.getPreviousStatus().name() : null,
                event.getNewStatus().name(),
                event.getPlanId(),
                event.getAmountXof(),
                event.getEffectiveMaxStudents(),
                event.getEffectiveMaxUsers(),
                event.getEffectiveMaxStorageMb(),
                event.getReason(),
                event.getActorId(),
                event.getOccurredAt());
    }
}
