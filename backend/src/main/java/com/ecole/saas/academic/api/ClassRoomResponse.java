package com.ecole.saas.academic.api;

import com.ecole.saas.academic.application.ClassRoomService.ClassRoomView;
import com.ecole.saas.academic.domain.ClassRoom;
import java.time.Instant;
import java.util.UUID;

public record ClassRoomResponse(
        UUID id,
        UUID academicYearId,
        String academicYearCode,
        UUID gradeLevelId,
        String gradeLevelCode,
        String gradeLevelName,
        String code,
        String name,
        String series,
        String roomName,
        int capacity,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static ClassRoomResponse from(ClassRoomView view) {
        ClassRoom room = view.classRoom();
        return new ClassRoomResponse(
                room.getId(),
                room.getAcademicYearId(),
                view.academicYearCode(),
                room.getGradeLevelId(),
                view.gradeLevelCode(),
                view.gradeLevelName(),
                room.getCode(),
                room.getName(),
                room.getSeries(),
                room.getRoomName(),
                room.getCapacity(),
                room.getStatus().name(),
                room.getCreatedAt(),
                room.getUpdatedAt());
    }
}
