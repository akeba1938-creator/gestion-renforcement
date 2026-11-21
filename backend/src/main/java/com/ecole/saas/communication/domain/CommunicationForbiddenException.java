package com.ecole.saas.communication.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class CommunicationForbiddenException extends BusinessException {

    public CommunicationForbiddenException(String detail) {
        super(ErrorCategory.FORBIDDEN, "communication-scope-forbidden", "Accès communication refusé", detail);
    }
}
