package com.ecole.saas.audit.api;

import com.ecole.saas.audit.application.AuditSummarySnapshot;
import java.time.Instant;
import java.util.Map;

public record AuditSummaryResponse(
        Instant from,
        Instant to,
        long totalEvents,
        Map<String, Long> countsByCategory,
        Map<String, Long> countsBySeverity,
        Map<String, Long> countsByOutcome) {

    public static AuditSummaryResponse from(AuditSummarySnapshot snapshot) {
        return new AuditSummaryResponse(
                snapshot.from(),
                snapshot.to(),
                snapshot.totalEvents(),
                snapshot.countsByCategory(),
                snapshot.countsBySeverity(),
                snapshot.countsByOutcome());
    }
}
