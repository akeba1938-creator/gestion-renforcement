package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SubjectNotFoundException extends BusinessException {

    public SubjectNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "subject-not-found", "Matière introuvable", detail);
    }
}
