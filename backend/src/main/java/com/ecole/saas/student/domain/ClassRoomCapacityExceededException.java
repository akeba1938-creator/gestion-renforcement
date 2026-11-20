package com.ecole.saas.student.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class ClassRoomCapacityExceededException extends BusinessException {

    public ClassRoomCapacityExceededException(String detail) {
        super(ErrorCategory.CONFLICT, "classroom-capacity-exceeded", "Capacité de la classe atteinte", detail);
    }
}
