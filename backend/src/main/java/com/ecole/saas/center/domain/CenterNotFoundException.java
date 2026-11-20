package com.ecole.saas.center.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class CenterNotFoundException extends BusinessException {

    public CenterNotFoundException(String problemCode, String title, String detail) {
        super(ErrorCategory.NOT_FOUND, problemCode, title, detail);
    }
}
