package com.ecole.saas.subscription.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SubscriptionQuotaExceededException extends BusinessException {

    public SubscriptionQuotaExceededException(String detail) {
        super(
                ErrorCategory.CONFLICT,
                "subscription-quota-exceeded",
                "Quota d'abonnement dépassé",
                detail);
    }
}
