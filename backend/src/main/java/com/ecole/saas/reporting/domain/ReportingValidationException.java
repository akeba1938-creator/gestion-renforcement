package com.ecole.saas.reporting.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class ReportingValidationException extends BusinessException {

    public ReportingValidationException(String detail) {
        super(ErrorCategory.BAD_REQUEST, "reporting-validation-error", "Paramètre de rapport invalide", detail);
    }

    public ReportingValidationException(String problemCode, String detail) {
        super(ErrorCategory.BAD_REQUEST, problemCode, "Paramètre de rapport invalide", detail);
    }
}
