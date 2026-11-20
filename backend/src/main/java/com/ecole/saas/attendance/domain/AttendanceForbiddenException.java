package com.ecole.saas.attendance.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class AttendanceForbiddenException extends BusinessException {

    public AttendanceForbiddenException(String detail) {
        super(
                ErrorCategory.FORBIDDEN,
                "teacher-assignment-forbidden",
                "Accès restreint aux classes et matières affectées",
                detail);
    }
}
