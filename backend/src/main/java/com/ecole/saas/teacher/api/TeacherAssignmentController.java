package com.ecole.saas.teacher.api;

import com.ecole.saas.teacher.application.TeacherAssignmentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Affectations des enseignants aux classes et matières par année scolaire.
 */
@RestController
@RequestMapping("/api/v1/teacher-assignments")
public class TeacherAssignmentController {

    private final TeacherAssignmentService assignmentService;

    public TeacherAssignmentController(TeacherAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<TeacherAssignmentResponse>> listAssignments(
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID classRoomId,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = TeacherPagination.of(
                page, size, sortBy, direction, TeacherPagination.ASSIGNMENT_SORT_FIELDS, "createdAt", Sort.Direction.DESC);
        Page<TeacherAssignmentResponse> assignments = assignmentService
                .listAssignments(teacherId, academicYearId, classRoomId, subjectId, active, pageable)
                .map(TeacherAssignmentResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(assignments));
    }

    @GetMapping("/{assignmentId}")
    public ResponseEntity<TeacherAssignmentResponse> getAssignment(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(TeacherAssignmentResponse.from(assignmentService.getAssignment(assignmentId)));
    }

    @PostMapping
    public ResponseEntity<TeacherAssignmentResponse> createAssignment(
            @Valid @RequestBody CreateTeacherAssignmentRequest request) {
        TeacherAssignmentService.AssignmentView created = assignmentService.createAssignment(
                request.teacherId(),
                request.academicYearId(),
                request.classRoomId(),
                request.subjectId(),
                Boolean.TRUE.equals(request.homeroomTeacher()),
                request.weeklyHours(),
                request.startDate(),
                request.endDate(),
                request.notes());
        return ResponseEntity.created(URI.create("/api/v1/teacher-assignments/" + created.assignment().getId()))
                .cacheControl(CacheControl.noStore())
                .body(TeacherAssignmentResponse.from(created));
    }

    @PutMapping("/{assignmentId}")
    public ResponseEntity<TeacherAssignmentResponse> updateAssignment(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody UpdateTeacherAssignmentRequest request) {
        TeacherAssignmentService.AssignmentView updated = assignmentService.updateAssignment(
                assignmentId,
                Boolean.TRUE.equals(request.homeroomTeacher()),
                request.weeklyHours(),
                request.startDate(),
                request.endDate(),
                request.notes());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(TeacherAssignmentResponse.from(updated));
    }

    @PostMapping("/{assignmentId}/deactivate")
    public ResponseEntity<TeacherAssignmentResponse> deactivateAssignment(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody(required = false) DeactivateTeacherAssignmentRequest request) {
        TeacherAssignmentService.AssignmentView deactivated = assignmentService.deactivateAssignment(
                assignmentId,
                request != null ? request.endDate() : null,
                request != null ? request.reason() : null);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(TeacherAssignmentResponse.from(deactivated));
    }
}
