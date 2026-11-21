package com.ecole.saas.academic.api;

import com.ecole.saas.academic.application.ClassRoomService;
import com.ecole.saas.academic.application.ClassRoomService.ClassRoomView;
import com.ecole.saas.academic.application.ClassRoomService.ClassSubjectView;
import com.ecole.saas.academic.domain.ClassRoomStatus;
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
 * Gestion des classes/groupes et des matières rattachées (avec coefficients et volumes horaires).
 */
@RestController
@RequestMapping("/api/v1/academic/classes")
public class ClassRoomController {

    private final ClassRoomService classRoomService;

    public ClassRoomController(ClassRoomService classRoomService) {
        this.classRoomService = classRoomService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ClassRoomResponse>> listClasses(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID gradeLevelId,
            @RequestParam(required = false) ClassRoomStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = AcademicPagination.of(
                page, size, sortBy, direction, AcademicPagination.CLASS_SORT_FIELDS, "name", Sort.Direction.ASC);
        Page<ClassRoomResponse> classes = classRoomService
                .listClassRooms(academicYearId, gradeLevelId, status, pageable)
                .map(ClassRoomResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(classes));
    }

    @GetMapping("/{classId}")
    public ResponseEntity<ClassRoomResponse> getClass(@PathVariable UUID classId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ClassRoomResponse.from(classRoomService.getClassRoom(classId)));
    }

    @PostMapping
    public ResponseEntity<ClassRoomResponse> createClass(@Valid @RequestBody CreateClassRoomRequest request) {
        ClassRoomView created = classRoomService.createClassRoom(
                request.academicYearId(),
                request.gradeLevelId(),
                request.code(),
                request.name(),
                request.series(),
                request.roomName(),
                request.capacity());
        return ResponseEntity.created(URI.create("/api/v1/academic/classes/" + created.classRoom().getId()))
                .cacheControl(CacheControl.noStore())
                .body(ClassRoomResponse.from(created));
    }

    @PutMapping("/{classId}")
    public ResponseEntity<ClassRoomResponse> updateClass(
            @PathVariable UUID classId,
            @Valid @RequestBody UpdateClassRoomRequest request) {
        ClassRoomView updated = classRoomService.updateClassRoom(
                classId,
                request.gradeLevelId(),
                request.name(),
                request.series(),
                request.roomName(),
                request.capacity());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ClassRoomResponse.from(updated));
    }

    @PostMapping("/{classId}/archive")
    public ResponseEntity<ClassRoomResponse> archiveClass(@PathVariable UUID classId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ClassRoomResponse.from(classRoomService.archiveClassRoom(classId)));
    }

    @PostMapping("/{classId}/reactivate")
    public ResponseEntity<ClassRoomResponse> reactivateClass(@PathVariable UUID classId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ClassRoomResponse.from(classRoomService.reactivateClassRoom(classId)));
    }

    @GetMapping("/{classId}/subjects")
    public ResponseEntity<PageResponse<ClassSubjectResponse>> listClassSubjects(
            @PathVariable UUID classId,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = AcademicPagination.of(
                page,
                size,
                sortBy,
                direction,
                AcademicPagination.CLASS_SUBJECT_SORT_FIELDS,
                "createdAt",
                Sort.Direction.ASC);
        Page<ClassSubjectResponse> subjects = classRoomService.listClassSubjects(classId, activeOnly, pageable)
                .map(ClassSubjectResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(subjects));
    }

    @PostMapping("/{classId}/subjects")
    public ResponseEntity<ClassSubjectResponse> assignSubject(
            @PathVariable UUID classId,
            @Valid @RequestBody AssignClassSubjectRequest request) {
        ClassSubjectView created = classRoomService.assignSubjectToClass(
                classId,
                request.subjectId(),
                request.coefficient(),
                request.weeklyHours(),
                request.mandatory());
        return ResponseEntity.created(URI.create(
                        "/api/v1/academic/classes/" + classId + "/subjects/" + created.classSubject().getId()))
                .cacheControl(CacheControl.noStore())
                .body(ClassSubjectResponse.from(created));
    }

    @PutMapping("/{classId}/subjects/{classSubjectId}")
    public ResponseEntity<ClassSubjectResponse> updateClassSubject(
            @PathVariable UUID classId,
            @PathVariable UUID classSubjectId,
            @Valid @RequestBody UpdateClassSubjectRequest request) {
        ClassSubjectView updated = classRoomService.updateClassSubject(
                classId,
                classSubjectId,
                request.coefficient(),
                request.weeklyHours(),
                Boolean.TRUE.equals(request.mandatory()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ClassSubjectResponse.from(updated));
    }

    @PostMapping("/{classId}/subjects/{classSubjectId}/activate")
    public ResponseEntity<ClassSubjectResponse> activateClassSubject(
            @PathVariable UUID classId,
            @PathVariable UUID classSubjectId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ClassSubjectResponse.from(classRoomService.activateClassSubject(classId, classSubjectId)));
    }

    @PostMapping("/{classId}/subjects/{classSubjectId}/deactivate")
    public ResponseEntity<ClassSubjectResponse> deactivateClassSubject(
            @PathVariable UUID classId,
            @PathVariable UUID classSubjectId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ClassSubjectResponse.from(classRoomService.deactivateClassSubject(classId, classSubjectId)));
    }
}
