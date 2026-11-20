package com.ecole.saas.student.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class InvalidEnrollmentStateException extends BusinessException {

    public InvalidEnrollmentStateException(String detail) {
        super(ErrorCategory.CONFLICT, "invalid-enrollment-state", "Opération d'inscription impossible", detail);
    }
}
