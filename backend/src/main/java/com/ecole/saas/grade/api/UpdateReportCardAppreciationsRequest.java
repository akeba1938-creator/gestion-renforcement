package com.ecole.saas.grade.api;

import com.ecole.saas.grade.domain.CouncilDecision;
import jakarta.validation.constraints.Size;

public record UpdateReportCardAppreciationsRequest(
        @Size(max = 1000)
        String homeroomAppreciation,
        @Size(max = 1000)
        String directorAppreciation,
        CouncilDecision councilDecision) {
}
