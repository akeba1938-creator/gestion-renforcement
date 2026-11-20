package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class ClassRoomAlreadyExistsException extends BusinessException {

    public ClassRoomAlreadyExistsException(String detail) {
        super(ErrorCategory.CONFLICT, "class-room-already-exists", "Classe déjà existante", detail);
    }
}
