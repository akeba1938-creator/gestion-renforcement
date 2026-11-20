package com.ecole.saas.student.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class StudentNotFoundException extends BusinessException {

    public StudentNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "student-not-found", "Élève introuvable", detail);
    }
}
