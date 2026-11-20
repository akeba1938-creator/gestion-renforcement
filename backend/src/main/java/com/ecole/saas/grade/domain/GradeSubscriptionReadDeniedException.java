package com.ecole.saas.grade.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class GradeSubscriptionReadDeniedException extends BusinessException {

    public GradeSubscriptionReadDeniedException(String detail) {
        super(
                ErrorCategory.FORBIDDEN,
                "subscription-access-denied",
                "Accès abonnement refusé",
                detail);
    }
}
