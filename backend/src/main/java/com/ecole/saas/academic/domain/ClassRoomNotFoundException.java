package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class ClassRoomNotFoundException extends BusinessException {

    public ClassRoomNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "class-room-not-found", "Classe introuvable", detail);
    }
}
