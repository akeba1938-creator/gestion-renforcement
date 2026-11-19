package com.ecole.saas.subscription.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class InvalidSubscriptionTransitionException extends BusinessException {

    public InvalidSubscriptionTransitionException(String detail) {
        super(
                ErrorCategory.CONFLICT,
                "invalid-subscription-transition",
                "Transition d'abonnement interdite",
                detail);
    }
}
