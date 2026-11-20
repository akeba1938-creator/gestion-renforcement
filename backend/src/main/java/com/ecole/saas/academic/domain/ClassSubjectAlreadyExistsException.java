package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class ClassSubjectAlreadyExistsException extends BusinessException {

    public ClassSubjectAlreadyExistsException(String detail) {
        super(
                ErrorCategory.CONFLICT,
                "class-subject-already-exists",
                "Matière déjà affectée à la classe",
                detail);
    }
}
