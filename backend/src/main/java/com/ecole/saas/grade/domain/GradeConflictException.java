package com.ecole.saas.grade.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class GradeConflictException extends BusinessException {

    public GradeConflictException(String problemCode, String title, String detail) {
        super(ErrorCategory.CONFLICT, problemCode, title, detail);
    }
}
