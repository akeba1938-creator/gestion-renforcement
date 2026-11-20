package com.ecole.saas.attendance.api;

import com.ecole.saas.attendance.domain.AttendanceTimeSlot;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateAttendanceSessionRequest(
        @NotNull(message = "La classe est obligatoire.")
        UUID classRoomId,
        UUID evaluationPeriodId,
        UUID subjectId,
        UUID teacherId,
        @NotNull(message = "La date de la séance est obligatoire.")
        LocalDate sessionDate,
        AttendanceTimeSlot timeSlot,
        LocalTime startTime,
        LocalTime endTime,
        @Size(max = 120)
        String label,
        @Size(max = 500)
        String notes,
        Boolean initializePresent) {
}
