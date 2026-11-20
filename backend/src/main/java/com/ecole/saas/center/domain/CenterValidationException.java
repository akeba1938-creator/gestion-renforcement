package com.ecole.saas.center.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class CenterValidationException extends BusinessException {

    public CenterValidationException(String problemCode, String title, String detail) {
        super(ErrorCategory.BAD_REQUEST, problemCode, title, detail);
    }
}
