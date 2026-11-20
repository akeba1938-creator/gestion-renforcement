package com.ecole.saas.student.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class StudentAccessForbiddenException extends BusinessException {

    public StudentAccessForbiddenException(String detail) {
        super(
                ErrorCategory.FORBIDDEN,
                "teacher-assignment-forbidden",
                "Accès restreint aux classes affectées",
                detail);
    }
}
