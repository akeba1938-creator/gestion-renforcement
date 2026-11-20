package com.ecole.saas.attendance.api;

import com.ecole.saas.attendance.domain.AttendanceRecordStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record UpsertAttendanceRecordsRequest(
        @NotEmpty(message = "La liste des pointages ne peut pas être vide.")
        List<@Valid AttendanceRecordItem> records) {

    public record AttendanceRecordItem(
            @NotNull(message = "L'élève est obligatoire.")
            UUID studentId,
            @NotNull(message = "Le statut d'assiduité est obligatoire.")
            AttendanceRecordStatus status,
            Integer lateMinutes,
            @Size(max = 500)
            String notes) {
    }
}
