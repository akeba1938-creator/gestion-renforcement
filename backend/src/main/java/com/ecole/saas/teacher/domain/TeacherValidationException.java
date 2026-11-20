package com.ecole.saas.teacher.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class TeacherValidationException extends BusinessException {

    public TeacherValidationException(String problemCode, String title, String detail) {
        super(ErrorCategory.BAD_REQUEST, problemCode, title, detail);
    }

    public TeacherValidationException(String detail) {
        super(ErrorCategory.BAD_REQUEST, "teacher-validation-error", "Données enseignant invalides", detail);
    }
}
