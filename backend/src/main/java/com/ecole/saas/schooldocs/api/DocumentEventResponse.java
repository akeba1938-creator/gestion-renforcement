package com.ecole.saas.schooldocs.api;

import com.ecole.saas.schooldocs.DocumentEventType;
import com.ecole.saas.schooldocs.domain.DocumentEvent;
import java.time.Instant;
import java.util.UUID;

public record DocumentEventResponse(
        UUID id,
        UUID documentId,
        int sequenceNo,
        DocumentEventType eventType,
        UUID actorId,
        String reason,
        UUID relatedDocumentId,
        Instant occurredAt) {

    public static DocumentEventResponse from(DocumentEvent event) {
        return new DocumentEventResponse(
                event.getId(),
                event.getDocumentId(),
                event.getSequenceNo(),
                event.getEventType(),
                event.getActorId(),
                event.getReason(),
                event.getRelatedDocumentId(),
                event.getOccurredAt());
    }
}
