package com.ecole.saas.grade.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class GradeValidationException extends BusinessException {

    public GradeValidationException(String detail) {
        super(ErrorCategory.BAD_REQUEST, "grade-validation-error", "Données de notation invalides", detail);
    }
}
