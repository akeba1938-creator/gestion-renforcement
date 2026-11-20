package com.ecole.saas.grade.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class GradeNotFoundException extends BusinessException {

    public GradeNotFoundException(String problemCode, String title, String detail) {
        super(ErrorCategory.NOT_FOUND, problemCode, title, detail);
    }

    public GradeNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "grade-not-found", "Ressource de notation introuvable", detail);
    }
}
