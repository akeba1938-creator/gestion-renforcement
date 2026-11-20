package com.ecole.saas.grade.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ComputeReportCardsRequest(
        @NotNull(message = "La classe est obligatoire.")
        UUID classRoomId,
        @NotNull(message = "La période d'évaluation est obligatoire.")
        UUID evaluationPeriodId) {
}
