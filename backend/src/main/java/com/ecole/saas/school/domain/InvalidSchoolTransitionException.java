package com.ecole.saas.school.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class InvalidSchoolTransitionException extends BusinessException {

    public InvalidSchoolTransitionException(String detail) {
        super(ErrorCategory.CONFLICT, "invalid-school-transition", "Transition de statut invalide", detail);
    }
}
