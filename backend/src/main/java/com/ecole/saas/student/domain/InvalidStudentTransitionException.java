package com.ecole.saas.student.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class InvalidStudentTransitionException extends BusinessException {

    public InvalidStudentTransitionException(String detail) {
        super(ErrorCategory.CONFLICT, "invalid-student-transition", "Transition de statut élève invalide", detail);
    }
}
