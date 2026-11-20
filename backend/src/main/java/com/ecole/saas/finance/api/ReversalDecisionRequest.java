package com.ecole.saas.finance.api;

import jakarta.validation.constraints.Size;

public record ReversalDecisionRequest(
        @Size(max = 500, message = "La note de décision ne doit pas dépasser 500 caractères.") String decisionNote) {
}
