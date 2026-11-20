package com.ecole.saas.grade.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class GradeForbiddenException extends BusinessException {

    public GradeForbiddenException(String detail) {
        super(
                ErrorCategory.FORBIDDEN,
                "teacher-assignment-forbidden",
                "Accès restreint aux classes et matières affectées",
                detail);
    }
}
