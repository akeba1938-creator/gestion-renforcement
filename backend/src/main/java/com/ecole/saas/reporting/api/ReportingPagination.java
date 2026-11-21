package com.ecole.saas.reporting.api;

import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class ReportingPagination {

    static final int MAX_SIZE = 100;
    static final Set<String> SNAPSHOT_SORT_FIELDS = Set.of(
            "snapshotNumber", "reportType", "outputFormat", "status", "fileSizeBytes", "generatedAt", "createdAt");

    private ReportingPagination() {
    }

    static Pageable of(
            int page,
            int size,
            String sortBy,
            String direction,
            Set<String> allowedFields,
            String defaultSort,
            Sort.Direction defaultDirection) {
        if (page < 0) {
            throw new IllegalArgumentException("Le numéro de page doit être supérieur ou égal à 0.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "La taille de page doit être comprise entre 1 et " + MAX_SIZE + ".");
        }
        String field = (sortBy == null || sortBy.isBlank()) ? defaultSort : sortBy.trim();
        if (!allowedFields.contains(field)) {
            throw new IllegalArgumentException("Le champ de tri demandé n'est pas autorisé.");
        }
        Sort.Direction sortDirection = parseDirection(direction, defaultDirection);
        return PageRequest.of(page, size, Sort.by(sortDirection, field));
    }

    private static Sort.Direction parseDirection(String direction, Sort.Direction defaultDirection) {
        if (direction == null || direction.isBlank()) {
            return defaultDirection;
        }
        return switch (direction.trim().toUpperCase(Locale.ROOT)) {
            case "ASC" -> Sort.Direction.ASC;
            case "DESC" -> Sort.Direction.DESC;
            default -> throw new IllegalArgumentException("La direction de tri doit être ASC ou DESC.");
        };
    }
}
