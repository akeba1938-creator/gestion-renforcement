package com.ecole.saas.family.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class FamilyArchivedException extends BusinessException {

    public FamilyArchivedException(String detail) {
        super(ErrorCategory.CONFLICT, "family-archived", "Famille archivée", detail);
    }
}
