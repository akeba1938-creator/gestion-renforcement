package com.ecole.saas.communication.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class CommunicationNotFoundException extends BusinessException {

    public CommunicationNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "communication-not-found", "Ressource de communication introuvable", detail);
    }
}
