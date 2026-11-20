package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class GradeLevelInactiveException extends BusinessException {

    public GradeLevelInactiveException(String detail) {
        super(ErrorCategory.CONFLICT, "grade-level-inactive", "Niveau inactif", detail);
    }
}
