package com.ecole.saas.center.api;

import com.ecole.saas.center.domain.CenterGroupEnrollmentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TransitionCenterEnrollmentRequest(
        @NotNull(message = "Le statut cible est obligatoire.")
        CenterGroupEnrollmentStatus status,
        LocalDate endedAt,
        @Size(max = 255, message = "Le motif ne doit pas dépasser 255 caractères.")
        String reason) {
}
