package com.ecole.saas.school.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SchoolAlreadyExistsException extends BusinessException {

    public SchoolAlreadyExistsException(String detail) {
        super(ErrorCategory.CONFLICT, "school-already-exists", "Établissement déjà existant", detail);
    }
}
