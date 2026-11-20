package com.ecole.saas.family.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class GuardianArchivedException extends BusinessException {

    public GuardianArchivedException(String detail) {
        super(ErrorCategory.CONFLICT, "guardian-archived", "Parent ou tuteur archivé", detail);
    }
}
