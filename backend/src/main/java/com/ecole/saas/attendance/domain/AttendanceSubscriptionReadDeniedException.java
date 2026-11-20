package com.ecole.saas.attendance.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class AttendanceSubscriptionReadDeniedException extends BusinessException {

    public AttendanceSubscriptionReadDeniedException(String detail) {
        super(
                ErrorCategory.FORBIDDEN,
                "subscription-access-denied",
                "Accès abonnement refusé",
                detail);
    }
}
