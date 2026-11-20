package com.ecole.saas.teacher.api;

import com.ecole.saas.teacher.application.TeacherAssignmentService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TeacherAssignmentResponse(
        UUID id,
        UUID teacherId,
        String teacherCode,
        String teacherFullName,
        UUID academicYearId,
        String academicYearCode,
        UUID classRoomId,
        String classRoomCode,
        String classRoomName,
        UUID subjectId,
        UUID classSubjectId,
        String subjectCode,
        String subjectName,
        boolean homeroomTeacher,
        Integer weeklyHours,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static TeacherAssignmentResponse from(TeacherAssignmentService.AssignmentView view) {
        return new TeacherAssignmentResponse(
                view.assignment().getId(),
                view.assignment().getTeacherId(),
                view.teacherCode(),
                view.teacherFullName(),
                view.assignment().getAcademicYearId(),
                view.academicYearCode(),
                view.assignment().getClassRoomId(),
                view.classRoomCode(),
                view.classRoomName(),
                view.assignment().getSubjectId(),
                view.assignment().getClassSubjectId(),
                view.subjectCode(),
                view.subjectName(),
                view.assignment().isHomeroomTeacher(),
                view.assignment().getWeeklyHours(),
                view.assignment().getStartDate(),
                view.assignment().getEndDate(),
                view.assignment().isActive(),
                view.assignment().getNotes(),
                view.assignment().getCreatedAt(),
                view.assignment().getUpdatedAt());
    }
}
