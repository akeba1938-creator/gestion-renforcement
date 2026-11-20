package com.ecole.saas.finance.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class FinanceConflictException extends BusinessException {

    public FinanceConflictException(String problemCode, String title, String detail) {
        super(ErrorCategory.CONFLICT, problemCode, title, detail);
    }
}
