package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SubjectAlreadyExistsException extends BusinessException {

    public SubjectAlreadyExistsException(String detail) {
        super(ErrorCategory.CONFLICT, "subject-already-exists", "Matière déjà existante", detail);
    }
}
