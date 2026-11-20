package com.ecole.saas.teacher.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class TeacherSubscriptionReadDeniedException extends BusinessException {

    public TeacherSubscriptionReadDeniedException(String detail) {
        super(
                ErrorCategory.FORBIDDEN,
                "subscription-access-denied",
                "Accès abonnement refusé",
                detail);
    }
}
