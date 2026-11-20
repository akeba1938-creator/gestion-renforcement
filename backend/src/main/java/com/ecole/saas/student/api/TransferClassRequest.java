package com.ecole.saas.student.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TransferClassRequest(
        @NotNull(message = "La classe cible est obligatoire.")
        UUID targetClassRoomId,

        @Size(max = 255, message = "Le motif ne peut pas dépasser 255 caractères.")
        String reason) {
}
