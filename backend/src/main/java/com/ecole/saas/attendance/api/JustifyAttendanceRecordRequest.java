package com.ecole.saas.attendance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JustifyAttendanceRecordRequest(
        @NotBlank(message = "Le motif de justification est obligatoire.")
        @Size(max = 500)
        String reason,
        @Size(max = 512)
        String documentRef) {
}
