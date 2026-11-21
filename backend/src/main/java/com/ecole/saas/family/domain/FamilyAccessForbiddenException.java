package com.ecole.saas.family.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class FamilyAccessForbiddenException extends BusinessException {

    public FamilyAccessForbiddenException(String detail) {
        super(
                ErrorCategory.FORBIDDEN,
                "family-scope-forbidden",
                "Accès restreint aux familles des élèves affectés",
                detail);
    }
}
