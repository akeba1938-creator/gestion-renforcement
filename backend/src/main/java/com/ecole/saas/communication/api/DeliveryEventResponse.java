package com.ecole.saas.communication.api;

import com.ecole.saas.communication.DeliveryStatus;
import com.ecole.saas.communication.domain.DeliveryEvent;
import com.ecole.saas.communication.domain.DeliveryFailureCode;
import java.time.Instant;
import java.util.UUID;

public record DeliveryEventResponse(
        UUID id,
        UUID deliveryId,
        int sequenceNo,
        DeliveryStatus previousStatus,
        DeliveryStatus newStatus,
        String providerName,
        String providerStatus,
        DeliveryFailureCode failureCode,
        String detail,
        Instant occurredAt) {

    public static DeliveryEventResponse from(DeliveryEvent event) {
        return new DeliveryEventResponse(
                event.getId(),
                event.getDeliveryId(),
                event.getSequenceNo(),
                event.getPreviousStatus(),
                event.getNewStatus(),
                event.getProviderName(),
                event.getProviderStatus(),
                event.getFailureCode(),
                event.getDetail(),
                event.getOccurredAt());
    }
}
