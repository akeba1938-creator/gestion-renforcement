package com.ecole.saas.schoolimport.api;

import com.ecole.saas.schoolimport.domain.SchoolImportValidationException;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class SchoolImportPagination {

    static final int MAX_PAGE_SIZE = 100;

    static final Set<String> JOB_SORT_FIELDS = Set.of(
            "createdAt", "analyzedAt", "executedAt", "jobNumber", "importType", "status", "totalRows");
    static final Set<String> ROW_SORT_FIELDS = Set.of(
            "rowNumber", "rowStatus", "plannedAction", "naturalKey");

    private SchoolImportPagination() {
    }

    static Pageable of(
            int page,
            int size,
            String sortBy,
            String direction,
            Set<String> allowedSortFields,
            String defaultSortField,
            Sort.Direction defaultDirection) {
        if (page < 0) {
            throw new SchoolImportValidationException("Le numéro de page doit être supérieur ou égal à 0.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new SchoolImportValidationException(
                    "La taille de page doit être comprise entre 1 et " + MAX_PAGE_SIZE + ".");
        }
        String effectiveSort = (sortBy == null || sortBy.isBlank()) ? defaultSortField : sortBy.trim();
        if (!allowedSortFields.contains(effectiveSort)) {
            throw new SchoolImportValidationException("Champ de tri non autorisé : " + effectiveSort);
        }
        Sort.Direction effectiveDir;
        if (direction == null || direction.isBlank()) {
            effectiveDir = defaultDirection;
        } else {
            effectiveDir = switch (direction.trim().toUpperCase(Locale.ROOT)) {
                case "ASC" -> Sort.Direction.ASC;
                case "DESC" -> Sort.Direction.DESC;
                default -> throw new SchoolImportValidationException(
                        "Direction de tri invalide (utilisez ASC ou DESC).");
            };
        }
        return PageRequest.of(page, size, Sort.by(effectiveDir, effectiveSort));
    }
}
