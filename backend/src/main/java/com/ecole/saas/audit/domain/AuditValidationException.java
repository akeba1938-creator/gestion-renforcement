package com.ecole.saas.audit.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class AuditValidationException extends BusinessException {

    public AuditValidationException(String message) {
        super(ErrorCategory.BAD_REQUEST, "audit-validation-error", "Paramètre d'audit invalide", message);
    }
}
