package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class AcademicYearNotFoundException extends BusinessException {

    public AcademicYearNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "academic-year-not-found", "Année scolaire introuvable", detail);
    }
}
