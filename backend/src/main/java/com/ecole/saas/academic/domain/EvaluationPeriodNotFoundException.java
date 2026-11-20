package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class EvaluationPeriodNotFoundException extends BusinessException {

    public EvaluationPeriodNotFoundException(String detail) {
        super(ErrorCategory.NOT_FOUND, "evaluation-period-not-found", "Période d'évaluation introuvable", detail);
    }
}
