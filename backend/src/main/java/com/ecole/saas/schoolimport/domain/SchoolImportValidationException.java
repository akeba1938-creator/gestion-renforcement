package com.ecole.saas.schoolimport.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SchoolImportValidationException extends BusinessException {

    public SchoolImportValidationException(String detail) {
        this("schoolimport-validation-error", "Données d'import invalides", detail);
    }

    public SchoolImportValidationException(String problemCode, String detail) {
        this(problemCode, "Données d'import invalides", detail);
    }

    public SchoolImportValidationException(String problemCode, String title, String detail) {
        super(ErrorCategory.BAD_REQUEST, problemCode, title, detail);
    }
}
