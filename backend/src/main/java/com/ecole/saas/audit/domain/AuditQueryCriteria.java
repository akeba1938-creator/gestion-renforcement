package com.ecole.saas.audit.domain;

import java.time.Instant;
import java.util.Locale;

/**
 * Règles pures de validation des critères de consultation et d'accès supervisé aux journaux d'audit.
 */
public final class AuditQueryCriteria {

    public static final int MIN_SUPPORT_ACCESS_REASON_LENGTH = 10;
    public static final int MAX_SUPPORT_ACCESS_REASON_LENGTH = 500;
    public static final int MAX_SEARCH_LENGTH = 120;

    private AuditQueryCriteria() {
    }

    public static void validateDateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new AuditValidationException(
                    "La date de début ('from') doit être antérieure ou égale à la date de fin ('to').");
        }
    }

    public static String requireSupportAccessReason(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            throw new AuditValidationException(
                    "Un motif explicite est obligatoire pour consulter le journal d'audit d'un établissement.");
        }
        String sanitized = AuditMetadataSanitizer.sanitizeText(rawReason, MAX_SUPPORT_ACCESS_REASON_LENGTH);
        if (sanitized == null || sanitized.length() < MIN_SUPPORT_ACCESS_REASON_LENGTH) {
            throw new AuditValidationException(
                    "Le motif d'accès doit contenir au moins " + MIN_SUPPORT_ACCESS_REASON_LENGTH + " caractères.");
        }
        return sanitized;
    }

    public static String normalizeResourceTypeFilter(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return null;
        }
        return resourceType.trim().toUpperCase(Locale.ROOT);
    }

    public static String normalizeSearchFilter(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String trimmed = search.trim();
        if (trimmed.length() > MAX_SEARCH_LENGTH) {
            throw new AuditValidationException(
                    "Le terme de recherche ne peut pas dépasser " + MAX_SEARCH_LENGTH + " caractères.");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
