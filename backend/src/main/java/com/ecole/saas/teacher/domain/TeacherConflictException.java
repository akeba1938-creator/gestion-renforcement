package com.ecole.saas.teacher.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class TeacherConflictException extends BusinessException {

    public TeacherConflictException(String problemCode, String title, String detail) {
        super(ErrorCategory.CONFLICT, problemCode, title, detail);
    }
}
