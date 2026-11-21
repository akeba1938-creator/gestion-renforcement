package com.ecole.saas.reporting.api;

import com.ecole.saas.reporting.ReportSnapshotEventType;
import com.ecole.saas.reporting.domain.ReportSnapshotEvent;
import java.time.Instant;
import java.util.UUID;

public record ReportSnapshotEventResponse(
        UUID id,
        UUID snapshotId,
        int sequenceNo,
        ReportSnapshotEventType eventType,
        UUID actorId,
        String message,
        Instant occurredAt) {

    static ReportSnapshotEventResponse from(ReportSnapshotEvent e) {
        return new ReportSnapshotEventResponse(
                e.getId(),
                e.getSnapshotId(),
                e.getSequenceNo(),
                e.getEventType(),
                e.getActorId(),
                e.getMessage(),
                e.getOccurredAt());
    }
}
