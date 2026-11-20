package com.ecole.saas.school.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SchoolNotActiveException extends BusinessException {

    public SchoolNotActiveException(String detail) {
        super(ErrorCategory.CONFLICT, "school-not-active", "Établissement inactif", detail);
    }
}
