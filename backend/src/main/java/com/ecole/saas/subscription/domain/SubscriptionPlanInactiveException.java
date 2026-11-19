package com.ecole.saas.subscription.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SubscriptionPlanInactiveException extends BusinessException {

    public SubscriptionPlanInactiveException(String detail) {
        super(
                ErrorCategory.CONFLICT,
                "subscription-plan-inactive",
                "Forfait inactif",
                detail);
    }
}
