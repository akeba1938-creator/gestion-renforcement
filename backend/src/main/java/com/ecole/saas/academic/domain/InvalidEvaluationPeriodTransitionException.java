package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.ErrorCategory;

public class InvalidEvaluationPeriodTransitionException extends BusinessException {

    public InvalidEvaluationPeriodTransitionException(String detail) {
        super(
                ErrorCategory.CONFLICT,
                "invalid-evaluation-period-transition",
                "Transition de période d'évaluation invalide",
                detail);
    }
}
