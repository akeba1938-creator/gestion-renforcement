package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class AcademicYearImmutableException extends BusinessException {

    public AcademicYearImmutableException(String detail) {
        super(ErrorCategory.CONFLICT, "academic-year-immutable", "Année scolaire non modifiable", detail);
    }
}
