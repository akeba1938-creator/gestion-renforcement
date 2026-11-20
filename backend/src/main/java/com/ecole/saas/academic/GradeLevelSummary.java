package com.ecole.saas.academic;

import java.util.UUID;

public record GradeLevelSummary(
        UUID id,
        String code,
        String name,
        String cycle,
        int displayOrder,
        boolean active) {
}
