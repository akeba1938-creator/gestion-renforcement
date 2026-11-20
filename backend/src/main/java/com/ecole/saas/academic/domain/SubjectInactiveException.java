package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SubjectInactiveException extends BusinessException {

    public SubjectInactiveException(String detail) {
        super(ErrorCategory.CONFLICT, "subject-inactive", "Matière inactive", detail);
    }
}
