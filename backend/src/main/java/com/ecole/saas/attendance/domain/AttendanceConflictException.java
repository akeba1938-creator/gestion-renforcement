package com.ecole.saas.attendance.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class AttendanceConflictException extends BusinessException {

    public AttendanceConflictException(String problemCode, String title, String detail) {
        super(ErrorCategory.CONFLICT, problemCode, title, detail);
    }
}
