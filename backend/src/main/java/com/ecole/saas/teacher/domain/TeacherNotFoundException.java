package com.ecole.saas.teacher.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class TeacherNotFoundException extends BusinessException {

    public TeacherNotFoundException(String problemCode, String title, String detail) {
        super(ErrorCategory.NOT_FOUND, problemCode, title, detail);
    }

    public TeacherNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "teacher-not-found", "Ressource enseignant introuvable", detail);
    }
}
