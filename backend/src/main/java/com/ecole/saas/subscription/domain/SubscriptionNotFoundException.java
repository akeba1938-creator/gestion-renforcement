package com.ecole.saas.subscription.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SubscriptionNotFoundException extends BusinessException {

    public SubscriptionNotFoundException(String detail) {
        super(
                ErrorCategory.NOT_FOUND,
                "subscription-not-found",
                "Abonnement introuvable",
                detail);
    }
}
