package com.ecole.saas.finance.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class FinanceSubscriptionReadDeniedException extends BusinessException {

    public FinanceSubscriptionReadDeniedException(String detail) {
        super(ErrorCategory.FORBIDDEN, "subscription-access-denied", "Accès abonnement refusé", detail);
    }
}
