package com.ecole.saas.student.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class StudentNotActiveException extends BusinessException {

    public StudentNotActiveException(String detail) {
        super(ErrorCategory.CONFLICT, "student-not-active", "Élève inactif", detail);
    }
}
