package com.ecole.saas.subscription.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class ActiveSubscriptionAlreadyExistsException extends BusinessException {

    public ActiveSubscriptionAlreadyExistsException(String detail) {
        super(
                ErrorCategory.CONFLICT,
                "active-subscription-already-exists",
                "Abonnement déjà ouvert",
                detail);
    }
}
