package com.ecole.saas.subscription.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SubscriptionAccessDeniedException extends BusinessException {

    public SubscriptionAccessDeniedException(String detail) {
        super(
                ErrorCategory.FORBIDDEN,
                "subscription-access-denied",
                "Abonnement non opérationnel",
                detail);
    }
}
