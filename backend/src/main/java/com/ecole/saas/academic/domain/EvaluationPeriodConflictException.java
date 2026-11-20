package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class EvaluationPeriodConflictException extends BusinessException {

    public EvaluationPeriodConflictException(String detail) {
        super(ErrorCategory.CONFLICT, "evaluation-period-conflict", "Conflit de période d'évaluation", detail);
    }
}
