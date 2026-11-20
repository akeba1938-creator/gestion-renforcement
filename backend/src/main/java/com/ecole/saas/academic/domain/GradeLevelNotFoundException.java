package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class GradeLevelNotFoundException extends BusinessException {

    public GradeLevelNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "grade-level-not-found", "Niveau introuvable", detail);
    }
}
