package com.ecole.saas.grade.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class EvaluationPeriodClosedException extends BusinessException {

    public EvaluationPeriodClosedException(String detail) {
        super(
                ErrorCategory.CONFLICT,
                "evaluation-period-closed",
                "Période d'évaluation non ouverte",
                detail);
    }
}
