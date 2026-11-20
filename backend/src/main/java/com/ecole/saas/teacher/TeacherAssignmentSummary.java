package com.ecole.saas.teacher;

import java.util.UUID;

/**
 * Vue immuable d'une affectation d'un enseignant à une classe et une matière pour une année scolaire.
 */
public record TeacherAssignmentSummary(
        UUID id,
        UUID teacherId,
        UUID teacherUserId,
        String teacherFullName,
        UUID academicYearId,
        UUID classRoomId,
        UUID subjectId,
        UUID classSubjectId,
        boolean homeroomTeacher,
        Integer weeklyHours,
        boolean active) {
}
