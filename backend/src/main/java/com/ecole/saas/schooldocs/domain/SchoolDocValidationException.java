package com.ecole.saas.schooldocs.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SchoolDocValidationException extends BusinessException {

    public SchoolDocValidationException(String detail) {
        super(ErrorCategory.BAD_REQUEST, "schooldocs-validation-error", "Données documentaires invalides", detail);
    }

    public SchoolDocValidationException(String problemCode, String title, String detail) {
        super(ErrorCategory.BAD_REQUEST, problemCode, title, detail);
    }
}
