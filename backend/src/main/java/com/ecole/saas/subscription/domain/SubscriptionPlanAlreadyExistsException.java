package com.ecole.saas.subscription.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SubscriptionPlanAlreadyExistsException extends BusinessException {

    public SubscriptionPlanAlreadyExistsException(String detail) {
        super(
                ErrorCategory.CONFLICT,
                "subscription-plan-already-exists",
                "Forfait déjà existant",
                detail);
    }
}
