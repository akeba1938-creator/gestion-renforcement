package com.ecole.saas.family.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class StudentArchivedForLinkException extends BusinessException {

    public StudentArchivedForLinkException(String detail) {
        super(ErrorCategory.CONFLICT, "student-archived", "Élève archivé", detail);
    }
}
