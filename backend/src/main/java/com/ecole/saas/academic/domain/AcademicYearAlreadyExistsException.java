package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class AcademicYearAlreadyExistsException extends BusinessException {

    public AcademicYearAlreadyExistsException(String detail) {
        super(ErrorCategory.CONFLICT, "academic-year-already-exists", "Année scolaire déjà existante", detail);
    }
}
