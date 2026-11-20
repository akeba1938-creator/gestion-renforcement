package com.ecole.saas.academic;

import java.util.UUID;

public record SubjectSummary(
        UUID id,
        String code,
        String name,
        String shortName,
        String category,
        int defaultCoefficient,
        boolean active) {
}
