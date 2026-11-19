package com.ecole.saas.identity.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class AccountLockedException extends BusinessException {

    public AccountLockedException(String detail) {
        super(
                ErrorCategory.LOCKED,
                "account-locked",
                "Compte temporairement verrouillé",
                detail);
    }
}
