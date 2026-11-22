package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class ClassRoomCapacityConflictException extends BusinessException {

    public ClassRoomCapacityConflictException(String detail) {
        super(ErrorCategory.CONFLICT, "class-room-capacity-exceeded", "Capacité insuffisante", detail);
    }
}
