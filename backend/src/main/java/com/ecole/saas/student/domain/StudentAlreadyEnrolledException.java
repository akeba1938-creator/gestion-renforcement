package com.ecole.saas.student.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class StudentAlreadyEnrolledException extends BusinessException {

    public StudentAlreadyEnrolledException(String detail) {
        super(ErrorCategory.CONFLICT, "student-already-enrolled", "Élève déjà inscrit", detail);
    }
}
