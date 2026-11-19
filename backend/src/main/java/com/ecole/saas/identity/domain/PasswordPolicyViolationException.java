package com.ecole.saas.identity.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class PasswordPolicyViolationException extends BusinessException {

    public PasswordPolicyViolationException(String message) {
        super(
                ErrorCategory.BAD_REQUEST,
                "password-policy-violation",
                "Mot de passe non conforme",
                message);
    }
}
