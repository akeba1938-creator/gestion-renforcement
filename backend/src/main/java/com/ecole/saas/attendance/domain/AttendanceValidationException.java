package com.ecole.saas.attendance.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class AttendanceValidationException extends BusinessException {

    public AttendanceValidationException(String detail) {
        super(ErrorCategory.BAD_REQUEST, "attendance-validation-error", "Données d'assiduité invalides", detail);
    }
}
