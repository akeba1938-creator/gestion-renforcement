package com.ecole.saas.center.api;

import com.ecole.saas.center.domain.CenterScheduleSlot;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CenterScheduleSlotResponse(
        UUID id,
        UUID groupId,
        UUID teacherId,
        UUID subjectId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        String roomName,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    static CenterScheduleSlotResponse from(CenterScheduleSlot slot) {
        return new CenterScheduleSlotResponse(
                slot.getId(),
                slot.getGroupId(),
                slot.getTeacherId(),
                slot.getSubjectId(),
                slot.getDayOfWeek(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getRoomName(),
                slot.getEffectiveFrom(),
                slot.getEffectiveUntil(),
                slot.isActive(),
                slot.getCreatedAt(),
                slot.getUpdatedAt());
    }
}
