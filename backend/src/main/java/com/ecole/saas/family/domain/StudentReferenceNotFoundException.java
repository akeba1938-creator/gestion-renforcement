package com.ecole.saas.family.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class StudentReferenceNotFoundException extends BusinessException {

    public StudentReferenceNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "student-not-found", "Élève introuvable", detail);
    }
}
