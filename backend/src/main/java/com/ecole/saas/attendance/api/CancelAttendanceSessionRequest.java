package com.ecole.saas.attendance.api;

import jakarta.validation.constraints.Size;

public record CancelAttendanceSessionRequest(
        @Size(max = 500)
        String reason) {
}
