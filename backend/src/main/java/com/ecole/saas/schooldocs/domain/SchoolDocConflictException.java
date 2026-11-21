package com.ecole.saas.schooldocs.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SchoolDocConflictException extends BusinessException {

    public SchoolDocConflictException(String problemCode, String detail) {
        super(ErrorCategory.CONFLICT, problemCode, "Conflit d'état documentaire", detail);
    }
}
