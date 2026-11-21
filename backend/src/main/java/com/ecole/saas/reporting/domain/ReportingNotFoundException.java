package com.ecole.saas.reporting.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class ReportingNotFoundException extends BusinessException {

    public ReportingNotFoundException(String problemCode, String detail) {
        super(ErrorCategory.NOT_FOUND, problemCode, "Ressource de reporting introuvable", detail);
    }
}
