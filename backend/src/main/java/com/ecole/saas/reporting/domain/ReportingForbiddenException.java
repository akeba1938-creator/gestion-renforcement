package com.ecole.saas.reporting.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class ReportingForbiddenException extends BusinessException {

    public ReportingForbiddenException(String problemCode, String detail) {
        super(ErrorCategory.FORBIDDEN, problemCode, "Accès au rapport refusé", detail);
    }
}
