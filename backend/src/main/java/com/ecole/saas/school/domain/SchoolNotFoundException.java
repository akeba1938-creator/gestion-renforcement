package com.ecole.saas.school.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SchoolNotFoundException extends BusinessException {

    public SchoolNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "school-not-found", "Établissement introuvable", detail);
    }
}
