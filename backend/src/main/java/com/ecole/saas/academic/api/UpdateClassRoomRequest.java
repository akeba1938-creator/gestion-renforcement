package com.ecole.saas.academic.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateClassRoomRequest(
        @NotNull(message = "Le niveau est obligatoire.")
        UUID gradeLevelId,
        @NotBlank(message = "Le nom de la classe est obligatoire.")
        @Size(max = 100, message = "Le nom de la classe ne peut pas dépasser 100 caractères.")
        String name,
        @Size(max = 30, message = "La série ne peut pas dépasser 30 caractères.")
        String series,
        @Size(max = 60, message = "Le nom de la salle ne peut pas dépasser 60 caractères.")
        String roomName,
        @NotNull(message = "La capacité de la classe est obligatoire.")
        @Min(value = 1, message = "La capacité doit être comprise entre 1 et 500 élèves.")
        @Max(value = 500, message = "La capacité doit être comprise entre 1 et 500 élèves.")
        Integer capacity) {
}
