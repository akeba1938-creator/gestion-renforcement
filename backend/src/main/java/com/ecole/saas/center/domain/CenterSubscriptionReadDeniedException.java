package com.ecole.saas.center.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class CenterSubscriptionReadDeniedException extends BusinessException {

    public CenterSubscriptionReadDeniedException(String detail) {
        super(
                ErrorCategory.FORBIDDEN,
                "subscription-access-denied",
                "Accès abonnement refusé",
                detail);
    }
}
