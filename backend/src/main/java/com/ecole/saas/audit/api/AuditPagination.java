package com.ecole.saas.audit.api;

import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class AuditPagination {

    static final int MAX_SIZE = 100;
    static final Set<String> AUDIT_SORT_FIELDS = Set.of(
            "occurredAt", "createdAt", "category", "action", "severity", "outcome");

    private AuditPagination() {
    }

    static Pageable of(int page, int size, String sortBy, String direction) {
        if (page < 0) {
            throw new IllegalArgumentException("Le numéro de page doit être supérieur ou égal à 0.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "La taille de page doit être comprise entre 1 et " + MAX_SIZE + ".");
        }
        String field = (sortBy == null || sortBy.isBlank()) ? "occurredAt" : sortBy.trim();
        if (!AUDIT_SORT_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Le champ de tri demandé n'est pas autorisé.");
        }
        Sort.Direction sortDirection = parseDirection(direction);
        return PageRequest.of(page, size, Sort.by(sortDirection, field).and(Sort.by(sortDirection, "id")));
    }

    private static Sort.Direction parseDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return Sort.Direction.DESC;
        }
        return switch (direction.trim().toUpperCase(Locale.ROOT)) {
            case "ASC" -> Sort.Direction.ASC;
            case "DESC" -> Sort.Direction.DESC;
            default -> throw new IllegalArgumentException("La direction de tri doit être ASC ou DESC.");
        };
    }
}
