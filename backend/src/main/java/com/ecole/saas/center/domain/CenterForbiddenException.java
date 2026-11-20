package com.ecole.saas.center.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class CenterForbiddenException extends BusinessException {

    public CenterForbiddenException(String problemCode, String title, String detail) {
        super(ErrorCategory.FORBIDDEN, problemCode, title, detail);
    }

    public static CenterForbiddenException tutorScope(String detail) {
        return new CenterForbiddenException(
                "center-tutor-scope-forbidden",
                "Périmètre intervenant non autorisé",
                detail);
    }
}
