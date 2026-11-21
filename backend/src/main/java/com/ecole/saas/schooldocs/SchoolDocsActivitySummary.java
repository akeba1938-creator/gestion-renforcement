package com.ecole.saas.schooldocs;

import java.util.Map;

/**
 * Vue consolidée immuable de l'activité documentaire scolaire exposée en lecture seule
 * aux modules aval (ex. {@code reporting}), avec montants de frais en entiers FCFA ({@code long}).
 */
public record SchoolDocsActivitySummary(
        long totalDocuments,
        long issuedCount,
        long revokedCount,
        long expiredCount,
        long duplicateCount,
        long totalFeeAmountXof,
        long totalStorageBytes,
        Map<DocumentType, Long> countsByType) {
}
