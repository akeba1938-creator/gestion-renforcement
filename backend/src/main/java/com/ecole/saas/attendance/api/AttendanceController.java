package com.ecole.saas.attendance.api;

import com.ecole.saas.attendance.application.AttendanceSessionService;
import com.ecole.saas.attendance.domain.AttendanceSessionStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestion des séances / feuilles d'appel, pointages de présence/retard/absence, justifications
 * et synthèses d'assiduité (tenant issu exclusivement du JWT côté serveur).
 */
@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceSessionService sessionService;

    public AttendanceController(AttendanceSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/sessions")
    public ResponseEntity<PageResponse<AttendanceSessionResponse>> listSessions(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID evaluationPeriodId,
            @RequestParam(required = false) UUID classRoomId,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) AttendanceSessionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "sessionDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = AttendancePagination.of(
                page, size, sortBy, direction, AttendancePagination.SESSION_SORT_FIELDS, "sessionDate", Sort.Direction.DESC);
        Page<AttendanceSessionResponse> sessions = sessionService
                .listSessions(academicYearId, evaluationPeriodId, classRoomId, subjectId, fromDate, toDate, status, pageable)
                .map(AttendanceSessionResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(sessions));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<AttendanceSessionResponse> getSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AttendanceSessionResponse.from(sessionService.getSession(sessionId)));
    }

    @PostMapping("/sessions")
    public ResponseEntity<AttendanceSessionResponse> createSession(
            @Valid @RequestBody CreateAttendanceSessionRequest request,
            JwtAuthenticationToken authentication) {
        AttendanceSessionService.AttendanceSessionView created = sessionService.createSession(
                request.classRoomId(),
                request.evaluationPeriodId(),
                request.subjectId(),
                request.teacherId(),
                request.sessionDate(),
                request.timeSlot(),
                request.startTime(),
                request.endTime(),
                request.label(),
                request.notes(),
                Boolean.TRUE.equals(request.initializePresent()),
                AttendanceApiSupport.extractActorId(authentication));
        return ResponseEntity.created(URI.create("/api/v1/attendance/sessions/" + created.session().getId()))
                .cacheControl(CacheControl.noStore())
                .body(AttendanceSessionResponse.from(created));
    }

    @PutMapping("/sessions/{sessionId}/records")
    public ResponseEntity<AttendanceSessionResponse> upsertRecords(
            @PathVariable UUID sessionId,
            @Valid @RequestBody UpsertAttendanceRecordsRequest request,
            JwtAuthenticationToken authentication) {
        List<AttendanceSessionService.AttendanceRecordInput> inputs = request.records().stream()
                .map(item -> new AttendanceSessionService.AttendanceRecordInput(
                        item.studentId(), item.status(), item.lateMinutes(), item.notes()))
                .toList();
        AttendanceSessionService.AttendanceSessionView updated = sessionService.upsertRecords(
                sessionId, inputs, AttendanceApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AttendanceSessionResponse.from(updated));
    }

    @PostMapping("/sessions/{sessionId}/submit")
    public ResponseEntity<AttendanceSessionResponse> submitSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AttendanceSessionResponse.from(sessionService.submitSession(sessionId)));
    }

    @PostMapping("/sessions/{sessionId}/lock")
    public ResponseEntity<AttendanceSessionResponse> lockSession(
            @PathVariable UUID sessionId,
            JwtAuthenticationToken authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AttendanceSessionResponse.from(
                        sessionService.lockSession(sessionId, AttendanceApiSupport.extractActorId(authentication))));
    }

    @PostMapping("/sessions/{sessionId}/cancel")
    public ResponseEntity<AttendanceSessionResponse> cancelSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody(required = false) CancelAttendanceSessionRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AttendanceSessionResponse.from(
                        sessionService.cancelSession(sessionId, request != null ? request.reason() : null)));
    }

    @PostMapping("/records/{recordId}/justify")
    public ResponseEntity<AttendanceRecordResponse> justifyRecord(
            @PathVariable UUID recordId,
            @Valid @RequestBody JustifyAttendanceRecordRequest request,
            JwtAuthenticationToken authentication) {
        AttendanceSessionService.AttendanceRecordView justified = sessionService.justifyRecord(
                recordId,
                request.reason(),
                request.documentRef(),
                AttendanceApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AttendanceRecordResponse.from(justified));
    }

    @GetMapping("/summaries/students/{studentId}")
    public ResponseEntity<StudentAttendanceSummaryResponse> getStudentSummary(
            @PathVariable UUID studentId,
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID evaluationPeriodId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(StudentAttendanceSummaryResponse.from(
                        sessionService.getStudentSummary(
                                studentId, academicYearId, evaluationPeriodId, fromDate, toDate)));
    }

    @GetMapping("/summaries/classes/{classRoomId}")
    public ResponseEntity<List<StudentAttendanceSummaryResponse>> getClassSummaries(
            @PathVariable UUID classRoomId,
            @RequestParam(required = false) UUID evaluationPeriodId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate) {
        List<StudentAttendanceSummaryResponse> summaries = sessionService
                .getClassSummaries(classRoomId, evaluationPeriodId, fromDate, toDate)
                .stream()
                .map(StudentAttendanceSummaryResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(summaries);
    }
}
