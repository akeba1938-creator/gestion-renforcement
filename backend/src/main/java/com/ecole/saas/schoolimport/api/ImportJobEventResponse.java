package com.ecole.saas.schoolimport.api;

import com.ecole.saas.schoolimport.ImportJobEventType;
import com.ecole.saas.schoolimport.domain.ImportJobEvent;
import java.time.Instant;
import java.util.UUID;

public record ImportJobEventResponse(
        UUID id,
        int sequenceNo,
        ImportJobEventType eventType,
        UUID actorId,
        String message,
        Instant occurredAt) {

    public static ImportJobEventResponse from(ImportJobEvent event) {
        return new ImportJobEventResponse(
                event.getId(),
                event.getSequenceNo(),
                event.getEventType(),
                event.getActorId(),
                event.getMessage(),
                event.getOccurredAt());
    }
}
