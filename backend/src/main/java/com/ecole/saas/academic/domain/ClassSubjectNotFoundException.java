package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class ClassSubjectNotFoundException extends BusinessException {

    public ClassSubjectNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "class-subject-not-found", "Matière de classe introuvable", detail);
    }
}
