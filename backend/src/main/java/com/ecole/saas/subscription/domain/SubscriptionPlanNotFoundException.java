package com.ecole.saas.subscription.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SubscriptionPlanNotFoundException extends BusinessException {

    public SubscriptionPlanNotFoundException(String detail) {
        super(
                ErrorCategory.NOT_FOUND,
                "subscription-plan-not-found",
                "Forfait introuvable",
                detail);
    }
}
