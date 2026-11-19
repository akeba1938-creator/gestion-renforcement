package com.ecole.saas.shared.domain;

import java.util.Objects;

/**
 * Exception métier de base traduite en {@code ProblemDetail} RFC 9457 par la couche API.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCategory category;
    private final String problemCode;
    private final String title;

    public BusinessException(ErrorCategory category, String problemCode, String title, String detail) {
        super(Objects.requireNonNull(detail, "Le message d'erreur est obligatoire."));
        this.category = Objects.requireNonNull(category, "La catégorie d'erreur est obligatoire.");
        this.problemCode = Objects.requireNonNull(problemCode, "Le code de problème est obligatoire.");
        this.title = Objects.requireNonNull(title, "Le titre d'erreur est obligatoire.");
    }

    public ErrorCategory getCategory() {
        return category;
    }

    public String getProblemCode() {
        return problemCode;
    }

    public String getTitle() {
        return title;
    }
}
