package com.ecole.saas.grade.api;

import jakarta.validation.constraints.Size;

public record CancelEvaluationRequest(
        @Size(max = 500)
        String reason) {
}
