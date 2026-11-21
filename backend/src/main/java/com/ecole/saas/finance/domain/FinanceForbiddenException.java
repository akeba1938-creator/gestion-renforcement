package com.ecole.saas.finance.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class FinanceForbiddenException extends BusinessException {

    public FinanceForbiddenException(String detail) {
        super(
                ErrorCategory.FORBIDDEN,
                "finance-self-approval-forbidden",
                "Auto-approbation interdite",
                detail);
    }
}
