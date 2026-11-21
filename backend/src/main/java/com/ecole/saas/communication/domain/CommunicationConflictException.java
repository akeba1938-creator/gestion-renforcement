package com.ecole.saas.communication.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class CommunicationConflictException extends BusinessException {

    public CommunicationConflictException(String problemCode, String detail) {
        super(ErrorCategory.CONFLICT, problemCode, "Conflit de communication", detail);
    }

    public CommunicationConflictException(String detail) {
        this("communication-conflict", detail);
    }
}
