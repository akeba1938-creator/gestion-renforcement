package com.ecole.saas.academic.api;

import com.ecole.saas.academic.application.ClassRoomService.ClassSubjectView;
import com.ecole.saas.academic.domain.ClassSubject;
import com.ecole.saas.academic.domain.Subject;
import java.time.Instant;
import java.util.UUID;

public record ClassSubjectResponse(
        UUID id,
        UUID classRoomId,
        UUID subjectId,
        String subjectCode,
        String subjectName,
        String subjectShortName,
        int coefficient,
        Integer weeklyHours,
        boolean mandatory,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static ClassSubjectResponse from(ClassSubjectView view) {
        ClassSubject cs = view.classSubject();
        Subject subject = view.subject();
        return new ClassSubjectResponse(
                cs.getId(),
                cs.getClassRoomId(),
                cs.getSubjectId(),
                subject != null ? subject.getCode() : null,
                subject != null ? subject.getName() : null,
                subject != null ? subject.getShortName() : null,
                cs.getCoefficient(),
                cs.getWeeklyHours(),
                cs.isMandatory(),
                cs.isActive(),
                cs.getCreatedAt(),
                cs.getUpdatedAt());
    }
}
