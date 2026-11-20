package com.ecole.saas.student.api;

import com.ecole.saas.student.application.StudentEnrollmentService;
import com.ecole.saas.student.application.StudentEnrollmentService.EnrollmentView;
import com.ecole.saas.student.domain.EnrollmentStatus;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestion des inscriptions annuelles des élèves et de leur affectation aux classes.
 */
@RestController
@RequestMapping("/api/v1/enrollments")
public class StudentEnrollmentController {

    private final StudentEnrollmentService enrollmentService;

    public StudentEnrollmentController(StudentEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<EnrollmentResponse>> listEnrollments(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID classRoomId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) EnrollmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "enrollmentDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = StudentPagination.of(
                page,
                size,
                sortBy,
                direction,
                StudentPagination.ENROLLMENT_SORT_FIELDS,
                "enrollmentDate",
                Sort.Direction.DESC);
        Page<EnrollmentResponse> enrollments = enrollmentService
                .listEnrollments(academicYearId, classRoomId, studentId, status, pageable)
                .map(EnrollmentResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(enrollments));
    }

    @GetMapping("/{enrollmentId}")
    public ResponseEntity<EnrollmentResponse> getEnrollment(@PathVariable UUID enrollmentId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EnrollmentResponse.from(enrollmentService.getEnrollment(enrollmentId)));
    }

    @PostMapping
    public ResponseEntity<EnrollmentResponse> enrollStudent(@Valid @RequestBody EnrollStudentRequest request) {
        EnrollmentView created = enrollmentService.enrollStudent(
                request.studentId(),
                request.academicYearId(),
                request.classRoomId(),
                request.enrollmentType(),
                request.enrollmentDate(),
                request.previousSchool(),
                request.repeating(),
                request.notes());
        return ResponseEntity.created(URI.create("/api/v1/enrollments/" + created.enrollment().getId()))
                .cacheControl(CacheControl.noStore())
                .body(EnrollmentResponse.from(created));
    }

    @PostMapping("/{enrollmentId}/transfer-class")
    public ResponseEntity<EnrollmentResponse> transferClass(
            @PathVariable UUID enrollmentId,
            @Valid @RequestBody TransferClassRequest request) {
        EnrollmentView updated = enrollmentService.transferClass(
                enrollmentId, request.targetClassRoomId(), request.reason());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EnrollmentResponse.from(updated));
    }

    @PostMapping("/{enrollmentId}/complete")
    public ResponseEntity<EnrollmentResponse> completeEnrollment(
            @PathVariable UUID enrollmentId,
            @Valid @RequestBody(required = false) CompleteEnrollmentRequest request) {
        EnrollmentView updated = enrollmentService.completeEnrollment(
                enrollmentId,
                request != null ? request.endDate() : null,
                request != null ? request.reason() : null);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EnrollmentResponse.from(updated));
    }

    @PostMapping("/{enrollmentId}/withdraw")
    public ResponseEntity<EnrollmentResponse> withdrawEnrollment(
            @PathVariable UUID enrollmentId,
            @Valid @RequestBody EnrollmentStatusActionRequest request) {
        EnrollmentView updated = enrollmentService.withdrawEnrollment(
                enrollmentId, request.endDate(), request.reason());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EnrollmentResponse.from(updated));
    }

    @PostMapping("/{enrollmentId}/transfer-out")
    public ResponseEntity<EnrollmentResponse> transferOutEnrollment(
            @PathVariable UUID enrollmentId,
            @Valid @RequestBody EnrollmentStatusActionRequest request) {
        EnrollmentView updated = enrollmentService.transferOutEnrollment(
                enrollmentId, request.endDate(), request.reason());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EnrollmentResponse.from(updated));
    }

    @PostMapping("/{enrollmentId}/cancel")
    public ResponseEntity<EnrollmentResponse> cancelEnrollment(
            @PathVariable UUID enrollmentId,
            @Valid @RequestBody StudentStatusReasonRequest request) {
        EnrollmentView updated = enrollmentService.cancelEnrollment(enrollmentId, request.reason());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EnrollmentResponse.from(updated));
    }
}
