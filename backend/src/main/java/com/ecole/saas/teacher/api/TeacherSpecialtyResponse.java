package com.ecole.saas.teacher.api;

import com.ecole.saas.teacher.application.TeacherService;
import java.time.Instant;
import java.util.UUID;

public record TeacherSpecialtyResponse(
        UUID id,
        UUID teacherId,
        UUID subjectId,
        String subjectCode,
        String subjectName,
        boolean primarySpecialty,
        String notes,
        Instant createdAt) {

    public static TeacherSpecialtyResponse from(TeacherService.SpecialtyView view) {
        return new TeacherSpecialtyResponse(
                view.specialty().getId(),
                view.specialty().getTeacherId(),
                view.specialty().getSubjectId(),
                view.subjectCode(),
                view.subjectName(),
                view.specialty().isPrimarySpecialty(),
                view.specialty().getNotes(),
                view.specialty().getCreatedAt());
    }
}
