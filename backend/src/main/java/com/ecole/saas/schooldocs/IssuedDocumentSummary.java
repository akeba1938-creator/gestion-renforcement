package com.ecole.saas.schooldocs;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue immuable en lecture seule d'un document scolaire officiel exposée aux autres modules
 * sans exposer les entités JPA internes de {@code schooldocs}.
 */
public record IssuedDocumentSummary(
        UUID id,
        String documentNumber,
        DocumentType documentType,
        DocumentStatus status,
        DocumentValidityStatus validityStatus,
        UUID originalDocumentId,
        int editionNumber,
        boolean duplicate,
        UUID studentId,
        UUID guardianId,
        UUID academicYearId,
        UUID classRoomId,
        String title,
        long feeAmountXof,
        long fileSizeBytes,
        String contentSha256,
        Instant issuedAt,
        Instant validUntil,
        Instant revokedAt) {
}
