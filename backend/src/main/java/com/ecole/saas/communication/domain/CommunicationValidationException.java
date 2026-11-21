package com.ecole.saas.communication.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class CommunicationValidationException extends BusinessException {

    public CommunicationValidationException(String detail) {
        super(ErrorCategory.BAD_REQUEST, "communication-validation-error", "Données de communication invalides", detail);
    }
}
