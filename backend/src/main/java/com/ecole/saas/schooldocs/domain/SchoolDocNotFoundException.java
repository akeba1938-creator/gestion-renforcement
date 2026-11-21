package com.ecole.saas.schooldocs.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SchoolDocNotFoundException extends BusinessException {

    public SchoolDocNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "schooldocs-not-found", "Ressource documentaire introuvable", detail);
    }

    public SchoolDocNotFoundException(String problemCode, String detail) {
        super(ErrorCategory.NOT_FOUND, problemCode, "Ressource documentaire introuvable", detail);
    }
}
