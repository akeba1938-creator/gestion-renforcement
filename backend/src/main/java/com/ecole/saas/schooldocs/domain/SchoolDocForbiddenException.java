package com.ecole.saas.schooldocs.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SchoolDocForbiddenException extends BusinessException {

    public SchoolDocForbiddenException(String problemCode, String detail) {
        super(ErrorCategory.FORBIDDEN, problemCode, "Accès refusé", detail);
    }

    public SchoolDocForbiddenException(String detail) {
        super(ErrorCategory.FORBIDDEN, "schooldocs-scope-forbidden", "Accès hors périmètre", detail);
    }
}
