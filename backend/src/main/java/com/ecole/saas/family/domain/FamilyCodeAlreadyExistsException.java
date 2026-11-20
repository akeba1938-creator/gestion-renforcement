package com.ecole.saas.family.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class FamilyCodeAlreadyExistsException extends BusinessException {

    public FamilyCodeAlreadyExistsException(String detail) {
        super(ErrorCategory.CONFLICT, "family-code-already-exists", "Code famille déjà utilisé", detail);
    }
}
