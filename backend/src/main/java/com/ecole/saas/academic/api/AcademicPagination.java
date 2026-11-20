package com.ecole.saas.academic.api;

import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class AcademicPagination {

    static final int MAX_SIZE = 100;

    static final Set<String> YEAR_SORT_FIELDS = Set.of("code", "label", "startDate", "endDate", "status", "createdAt");
    static final Set<String> LEVEL_SORT_FIELDS = Set.of("displayOrder", "code", "name", "cycle", "createdAt");
    static final Set<String> SUBJECT_SORT_FIELDS = Set.of("code", "name", "category", "defaultCoefficient", "createdAt");
    static final Set<String> CLASS_SORT_FIELDS = Set.of("code", "name", "series", "capacity", "status", "createdAt");
    static final Set<String> CLASS_SUBJECT_SORT_FIELDS = Set.of("coefficient", "weeklyHours", "mandatory", "createdAt");

    private AcademicPagination() {
    }

    static Pageable of(
            int page,
            int size,
            String sortBy,
            String direction,
            Set<String> allowedFields,
            String defaultSort,
            Sort.Direction defaultDirection) {
        validatePageBounds(page, size);
        String field = (sortBy == null || sortBy.isBlank()) ? defaultSort : sortBy.trim();
        if (!allowedFields.contains(field)) {
            throw new IllegalArgumentException("Le champ de tri demandé n'est pas autorisé.");
        }
        Sort.Direction sortDirection = parseDirection(direction, defaultDirection);
        return PageRequest.of(page, size, Sort.by(sortDirection, field));
    }

    static Pageable ofFixedOrder(int page, int size) {
        validatePageBounds(page, size);
        return PageRequest.of(page, size);
    }

    private static void validatePageBounds(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Le numéro de page doit être supérieur ou égal à 0.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "La taille de page doit être comprise entre 1 et " + MAX_SIZE + ".");
        }
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
