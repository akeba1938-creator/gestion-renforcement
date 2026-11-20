package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class GradeLevelAlreadyExistsException extends BusinessException {

    public GradeLevelAlreadyExistsException(String detail) {
        super(ErrorCategory.CONFLICT, "grade-level-already-exists", "Niveau déjà existant", detail);
    }
}
