package com.ecole.saas.student.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class AcademicReferenceNotFoundException extends BusinessException {

    public AcademicReferenceNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "academic-reference-not-found", "Référence académique introuvable", detail);
    }
}
