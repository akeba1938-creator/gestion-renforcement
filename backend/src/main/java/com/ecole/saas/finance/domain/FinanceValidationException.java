package com.ecole.saas.finance.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class FinanceValidationException extends BusinessException {

    public FinanceValidationException(String problemCode, String title, String detail) {
        super(ErrorCategory.BAD_REQUEST, problemCode, title, detail);
    }
}
