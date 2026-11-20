package com.ecole.saas.academic;

import java.util.UUID;

public record ClassSubjectSummary(
        UUID id,
        UUID classRoomId,
        UUID subjectId,
        String subjectCode,
        String subjectName,
        int coefficient,
        Integer weeklyHours,
        boolean mandatory,
        boolean active) {
}
