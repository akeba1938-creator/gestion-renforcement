package com.ecole.saas.family.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class StudentGuardianLinkAlreadyExistsException extends BusinessException {

    public StudentGuardianLinkAlreadyExistsException(String detail) {
        super(ErrorCategory.CONFLICT, "student-guardian-link-already-exists", "Lien élève-tuteur déjà existant", detail);
    }
}
