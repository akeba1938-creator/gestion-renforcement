package com.ecole.saas.subscription.api;

import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class SubscriptionPagination {

    static final int DEFAULT_PAGE = 0;
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    static final Set<String> PLAN_SORT_FIELDS = Set.of(
            "code", "name", "monthlyPriceXof", "yearlyPriceXof", "maxStudents", "createdAt");
    static final Set<String> SUBSCRIPTION_SORT_FIELDS = Set.of(
            "createdAt", "startsAt", "endsAt", "status", "amountXof");

    private SubscriptionPagination() {
    }

    static Pageable of(int page, int size, String sortBy, String direction, Set<String> allowedFields, String defaultSort) {
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
        Sort.Direction sortDirection = parseDirection(direction);
        return PageRequest.of(page, size, Sort.by(sortDirection, field));
    }

    static Pageable ofFixedOrder(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Le numéro de page doit être supérieur ou égal à 0.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "La taille de page doit être comprise entre 1 et " + MAX_SIZE + ".");
        }
        return PageRequest.of(page, size);
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
