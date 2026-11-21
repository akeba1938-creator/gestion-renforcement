package com.ecole.saas.schoolimport.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SchoolImportForbiddenException extends BusinessException {

    public SchoolImportForbiddenException(String detail) {
        this("schoolimport-scope-forbidden", "Accès d'import refusé", detail);
    }

    public SchoolImportForbiddenException(String problemCode, String title, String detail) {
        super(ErrorCategory.FORBIDDEN, problemCode, title, detail);
    }
}
