package com.ecole.saas.audit.application;

import java.time.Instant;
import java.util.Map;

public record AuditSummarySnapshot(
        Instant from,
        Instant to,
        long totalEvents,
        Map<String, Long> countsByCategory,
        Map<String, Long> countsBySeverity,
        Map<String, Long> countsByOutcome) {
}
