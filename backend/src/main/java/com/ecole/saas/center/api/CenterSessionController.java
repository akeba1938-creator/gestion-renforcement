package com.ecole.saas.center.api;

import com.ecole.saas.center.application.CenterSessionService;
import com.ecole.saas.center.domain.CenterSessionStatus;
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
 * Gestion des créneaux hebdomadaires, des séances planifiées (unitaires ou générées)
 * et du suivi de présence / participation des élèves.
 */
@RestController
@RequestMapping("/api/v1/center")
public class CenterSessionController {

    private final CenterSessionService sessionService;

    public CenterSessionController(CenterSessionService sessionService) {
        this.sessionService = sessionService;
    }

    // --- Créneaux hebdomadaires ---

    @GetMapping("/groups/{groupId}/schedule-slots")
    public ResponseEntity<List<CenterScheduleSlotResponse>> listScheduleSlots(@PathVariable UUID groupId) {
        List<CenterScheduleSlotResponse> slots = sessionService.listScheduleSlots(groupId).stream()
                .map(CenterScheduleSlotResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(slots);
    }

    @PostMapping("/groups/{groupId}/schedule-slots")
    public ResponseEntity<CenterScheduleSlotResponse> createScheduleSlot(
            @PathVariable UUID groupId,
            @Valid @RequestBody UpsertCenterScheduleSlotRequest request) {
        CenterScheduleSlotResponse created = CenterScheduleSlotResponse.from(
                sessionService.createScheduleSlot(
                        groupId,
                        request.teacherId(),
                        request.subjectId(),
                        request.dayOfWeek(),
                        request.startTime(),
                        request.endTime(),
                        request.roomName(),
                        request.effectiveFrom(),
                        request.effectiveUntil()));
        return ResponseEntity.created(URI.create("/api/v1/center/schedule-slots/" + created.id()))
                .cacheControl(CacheControl.noStore())
                .body(created);
    }

    @PutMapping("/schedule-slots/{slotId}")
    public ResponseEntity<CenterScheduleSlotResponse> updateScheduleSlot(
            @PathVariable UUID slotId,
            @Valid @RequestBody UpsertCenterScheduleSlotRequest request) {
        CenterScheduleSlotResponse updated = CenterScheduleSlotResponse.from(
                sessionService.updateScheduleSlot(
                        slotId,
                        request.teacherId(),
                        request.subjectId(),
                        request.dayOfWeek(),
                        request.startTime(),
                        request.endTime(),
                        request.roomName(),
                        request.effectiveFrom(),
                        request.effectiveUntil()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(updated);
    }

    @PostMapping("/schedule-slots/{slotId}/deactivate")
    public ResponseEntity<CenterScheduleSlotResponse> deactivateScheduleSlot(@PathVariable UUID slotId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterScheduleSlotResponse.from(sessionService.deactivateScheduleSlot(slotId)));
    }

    // --- Séances ---

    @GetMapping("/sessions")
    public ResponseEntity<PageResponse<CenterSessionResponse>> listSessions(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) CenterSessionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "sessionDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = CenterPagination.of(
                page, size, sortBy, direction, CenterPagination.SESSION_SORT_FIELDS, "sessionDate", Sort.Direction.DESC);
        Page<CenterSessionResponse> sessions = sessionService
                .listSessions(groupId, teacherId, fromDate, toDate, status, pageable)
                .map(CenterSessionResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(sessions));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<CenterSessionResponse> getSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterSessionResponse.from(sessionService.getSession(sessionId)));
    }

    @PostMapping("/sessions")
    public ResponseEntity<CenterSessionResponse> createSession(
            @Valid @RequestBody CreateCenterSessionRequest request,
            JwtAuthenticationToken authentication) {
        CenterSessionService.SessionView created = sessionService.createSession(
                request.groupId(),
                request.scheduleSlotId(),
                request.teacherId(),
                request.subjectId(),
                request.sessionDate(),
                request.startTime(),
                request.endTime(),
                request.roomName(),
                request.topic(),
                request.notes(),
                CenterApiSupport.extractActorId(authentication));
        return ResponseEntity.created(URI.create("/api/v1/center/sessions/" + created.session().getId()))
                .cacheControl(CacheControl.noStore())
                .body(CenterSessionResponse.from(created));
    }

    @PostMapping("/groups/{groupId}/sessions/generate")
    public ResponseEntity<GeneratedCenterSessionsResponse> generateSessions(
            @PathVariable UUID groupId,
            @Valid @RequestBody GenerateCenterSessionsRequest request,
            JwtAuthenticationToken authentication) {
        CenterSessionService.GeneratedSessionsResult result = sessionService.generateSessions(
                groupId,
                request.fromDate(),
                request.toDate(),
                CenterApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(GeneratedCenterSessionsResponse.from(result));
    }

    @PostMapping("/sessions/{sessionId}/start")
    public ResponseEntity<CenterSessionResponse> startSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterSessionResponse.from(sessionService.startSession(sessionId)));
    }

    @PostMapping("/sessions/{sessionId}/complete")
    public ResponseEntity<CenterSessionResponse> completeSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody(required = false) CompleteCenterSessionRequest request,
            JwtAuthenticationToken authentication) {
        CenterSessionService.SessionView completed = sessionService.completeSession(
                sessionId,
                request != null ? request.topic() : null,
                request != null ? request.notes() : null,
                CenterApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterSessionResponse.from(completed));
    }

    @PostMapping("/sessions/{sessionId}/cancel")
    public ResponseEntity<CenterSessionResponse> cancelSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CenterReasonRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterSessionResponse.from(sessionService.cancelSession(sessionId, request.reason())));
    }

    @PutMapping("/sessions/{sessionId}/attendance")
    public ResponseEntity<CenterSessionResponse> upsertAttendance(
            @PathVariable UUID sessionId,
            @Valid @RequestBody UpsertCenterAttendanceRequest request,
            JwtAuthenticationToken authentication) {
        List<CenterSessionService.AttendanceInput> inputs = request.records().stream()
                .map(item -> new CenterSessionService.AttendanceInput(
                        item.studentId(),
                        item.status(),
                        item.lateMinutes(),
                        item.participationRating(),
                        item.homeworkDone(),
                        item.remarks()))
                .toList();
        CenterSessionService.SessionView updated = sessionService.upsertAttendance(
                sessionId, inputs, CenterApiSupport.extractActorId(authentication));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterSessionResponse.from(updated));
    }
}
