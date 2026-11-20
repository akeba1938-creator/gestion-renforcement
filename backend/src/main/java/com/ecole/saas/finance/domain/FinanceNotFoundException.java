package com.ecole.saas.finance.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class FinanceNotFoundException extends BusinessException {

    public FinanceNotFoundException(String problemCode, String title, String detail) {
        super(ErrorCategory.NOT_FOUND, problemCode, title, detail);
    }
}
