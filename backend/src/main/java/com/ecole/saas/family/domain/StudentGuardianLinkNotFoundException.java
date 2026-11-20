package com.ecole.saas.family.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class StudentGuardianLinkNotFoundException extends BusinessException {

    public StudentGuardianLinkNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "student-guardian-link-not-found", "Lien élève-tuteur introuvable", detail);
    }
}
