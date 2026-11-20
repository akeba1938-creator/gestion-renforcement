package com.ecole.saas.family.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class GuardianNotFoundException extends BusinessException {

    public GuardianNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "guardian-not-found", "Parent ou tuteur introuvable", detail);
    }
}
