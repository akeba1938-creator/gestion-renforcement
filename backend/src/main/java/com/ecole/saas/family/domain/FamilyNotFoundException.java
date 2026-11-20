package com.ecole.saas.family.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class FamilyNotFoundException extends BusinessException {

    public FamilyNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "family-not-found", "Famille introuvable", detail);
    }
}
