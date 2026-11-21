package com.ecole.saas.audit.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class AuditLogNotFoundException extends BusinessException {

    public AuditLogNotFoundException(String message) {
        super(ErrorCategory.NOT_FOUND, "audit-log-not-found", "Entrée d'audit introuvable", message);
    }
}
