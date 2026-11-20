package com.ecole.saas.student.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class StudentSubscriptionReadDeniedException extends BusinessException {

    public StudentSubscriptionReadDeniedException(String detail) {
        super(ErrorCategory.FORBIDDEN, "subscription-access-denied", "Accès abonnement refusé", detail);
    }
}
