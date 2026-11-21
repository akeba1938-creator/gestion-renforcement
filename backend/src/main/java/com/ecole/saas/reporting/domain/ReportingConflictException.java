package com.ecole.saas.reporting.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class ReportingConflictException extends BusinessException {

    public ReportingConflictException(String problemCode, String detail) {
        super(ErrorCategory.CONFLICT, problemCode, "Conflit sur le rapport", detail);
    }
}
