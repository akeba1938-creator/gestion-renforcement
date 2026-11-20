package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class ClassRoomArchivedException extends BusinessException {

    public ClassRoomArchivedException(String detail) {
        super(ErrorCategory.CONFLICT, "class-room-archived", "Classe archivée", detail);
    }
}
