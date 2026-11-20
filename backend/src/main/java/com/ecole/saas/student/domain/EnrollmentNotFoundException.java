package com.ecole.saas.student.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class EnrollmentNotFoundException extends BusinessException {

    public EnrollmentNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "enrollment-not-found", "Inscription introuvable", detail);
    }
}
