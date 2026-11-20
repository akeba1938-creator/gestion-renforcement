package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class InvalidAcademicYearTransitionException extends BusinessException {

    public InvalidAcademicYearTransitionException(String detail) {
        super(ErrorCategory.CONFLICT, "invalid-academic-year-transition", "Transition d'année scolaire invalide", detail);
    }
}
