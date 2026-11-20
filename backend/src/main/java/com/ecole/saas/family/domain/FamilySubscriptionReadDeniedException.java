package com.ecole.saas.family.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class FamilySubscriptionReadDeniedException extends BusinessException {

    public FamilySubscriptionReadDeniedException(String detail) {
        super(ErrorCategory.FORBIDDEN, "subscription-access-denied", "Accès abonnement refusé", detail);
    }
}
