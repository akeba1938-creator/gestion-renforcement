package com.ecole.saas.academic;

import java.util.UUID;

public record ClassRoomSummary(
        UUID id,
        UUID academicYearId,
        UUID gradeLevelId,
        String code,
        String name,
        String series,
        String roomName,
        int capacity,
        String status) {
}
