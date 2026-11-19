package com.ecole.saas.identity.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class AuthenticationFailedException extends BusinessException {

    public AuthenticationFailedException(String detail) {
        super(
                ErrorCategory.UNAUTHORIZED,
                "authentication-failed",
                "Authentification échouée",
                detail);
    }
}
