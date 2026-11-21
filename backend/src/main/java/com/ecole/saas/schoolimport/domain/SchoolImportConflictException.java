package com.ecole.saas.schoolimport.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SchoolImportConflictException extends BusinessException {

    public SchoolImportConflictException(String problemCode, String detail) {
        this(problemCode, "Conflit d'import", detail);
    }

    public SchoolImportConflictException(String problemCode, String title, String detail) {
        super(ErrorCategory.CONFLICT, problemCode, title, detail);
    }
}
