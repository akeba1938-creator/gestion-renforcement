package com.ecole.saas.communication.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class CommunicationSubscriptionReadDeniedException extends BusinessException {

    public CommunicationSubscriptionReadDeniedException(String detail) {
        super(ErrorCategory.FORBIDDEN, "subscription-access-denied", "Accès bloqué par l'abonnement", detail);
    }
}
