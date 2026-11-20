package com.ecole.saas.attendance.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class AttendanceNotFoundException extends BusinessException {

    public AttendanceNotFoundException(String problemCode, String title, String detail) {
        super(ErrorCategory.NOT_FOUND, problemCode, title, detail);
    }

    public AttendanceNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "attendance-not-found", "Ressource d'assiduité introuvable", detail);
    }
}
