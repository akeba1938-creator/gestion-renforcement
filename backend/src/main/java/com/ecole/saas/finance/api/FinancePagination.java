package com.ecole.saas.finance.api;

import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class FinancePagination {

    static final int MAX_SIZE = 100;

    static final Set<String> FEE_ITEM_SORT_FIELDS =
            Set.of("code", "label", "category", "amountXof", "active", "createdAt");
    static final Set<String> INVOICE_SORT_FIELDS =
            Set.of("invoiceNumber", "dueDate", "issueDate", "netAmountXof", "balanceXof", "status", "createdAt");
    static final Set<String> ADJUSTMENT_SORT_FIELDS =
            Set.of("requestedAt", "decidedAt", "resolvedAmountXof", "status", "adjustmentType");
    static final Set<String> PAYMENT_SORT_FIELDS =
            Set.of("paidAt", "amountXof", "paymentMethod", "createdAt");
    static final Set<String> RECEIPT_SORT_FIELDS =
            Set.of("receiptNumber", "issuedAt", "amountXof", "createdAt");
    static final Set<String> REVERSAL_REQUEST_SORT_FIELDS =
            Set.of("requestedAt", "decidedAt", "amountXof", "status");
    static final Set<String> LEDGER_SORT_FIELDS =
            Set.of("sequenceNo", "occurredAt", "entryType", "direction", "amountXof");
    static final Set<String> CASH_CLOSING_SORT_FIELDS =
            Set.of("closingDate", "expectedCashXof", "countedCashXof", "discrepancyXof", "status", "submittedAt");

    private FinancePagination() {
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
