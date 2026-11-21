package com.ecole.saas.schooldocs.api;

import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schooldocs.application.IssuedDocumentService.SchoolDocsSummaryView;
import java.util.Map;

public record SchoolDocsSummaryResponse(
        long activeTemplates,
        long inactiveTemplates,
        long totalDocuments,
        long validDocuments,
        long expiredDocuments,
        long revokedDocuments,
        long duplicateDocuments,
        long totalStorageBytes,
        long totalStorageMiB,
        long totalFeeAmountXof,
        Map<DocumentType, Long> documentsByType) {

    public static SchoolDocsSummaryResponse from(SchoolDocsSummaryView view) {
        return new SchoolDocsSummaryResponse(
                view.activeTemplates(),
                view.inactiveTemplates(),
                view.totalDocuments(),
                view.validDocuments(),
                view.expiredDocuments(),
                view.revokedDocuments(),
                view.duplicateDocuments(),
                view.totalStorageBytes(),
                view.totalStorageMiB(),
                view.totalFeeAmountXof(),
                view.documentsByType());
    }
}
