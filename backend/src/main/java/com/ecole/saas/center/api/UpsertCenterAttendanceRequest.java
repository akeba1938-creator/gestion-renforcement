package com.ecole.saas.center.api;

import com.ecole.saas.center.domain.CenterAttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record UpsertCenterAttendanceRequest(
        @NotEmpty(message = "Au moins un pointage est requis.")
        @Valid
        List<AttendanceItem> records) {

    public record AttendanceItem(
            @NotNull(message = "L'identifiant de l'élève est obligatoire.")
            UUID studentId,
            @NotNull(message = "Le statut de présence est obligatoire.")
            CenterAttendanceStatus status,
            @Min(value = 1, message = "Le retard doit être d'au moins 1 minute.")
            @Max(value = 300, message = "Le retard ne peut pas dépasser 300 minutes.")
            Integer lateMinutes,
            @Min(value = 1, message = "La note de participation doit être comprise entre 1 et 5.")
            @Max(value = 5, message = "La note de participation doit être comprise entre 1 et 5.")
            Integer participationRating,
            Boolean homeworkDone,
            @Size(max = 500, message = "Les remarques ne doivent pas dépasser 500 caractères.")
            String remarks) {
    }
}
