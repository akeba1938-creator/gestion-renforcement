package com.ecole.saas.schoolimport.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class SchoolImportNotFoundException extends BusinessException {

    public SchoolImportNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "schoolimport-not-found", "Lot d'import introuvable", detail);
    }

    public SchoolImportNotFoundException(String problemCode, String detail) {
        super(ErrorCategory.NOT_FOUND, problemCode, "Ressource introuvable", detail);
    }
}
