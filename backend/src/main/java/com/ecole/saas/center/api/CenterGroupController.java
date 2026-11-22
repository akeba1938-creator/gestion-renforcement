package com.ecole.saas.center.api;

import com.ecole.saas.center.application.CenterGroupService;
import com.ecole.saas.center.domain.CenterGroupStatus;
import com.ecole.saas.center.domain.CenterGroupType;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestion des cohortes de renforcement, des affectations d'intervenants et des inscriptions élèves.
 */
@RestController
@RequestMapping("/api/v1/center/groups")
public class CenterGroupController {

    private final CenterGroupService groupService;

    public CenterGroupController(CenterGroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<CenterGroupResponse>> listGroups(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID gradeLevelId,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) CenterGroupType groupType,
            @RequestParam(required = false) CenterGroupStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = CenterPagination.of(
                page, size, sortBy, direction, CenterPagination.GROUP_SORT_FIELDS, "code", Sort.Direction.ASC);
        Page<CenterGroupResponse> result = groupService
                .listGroups(academicYearId, gradeLevelId, subjectId, groupType, status, pageable)
                .map(CenterGroupResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(result));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<CenterGroupResponse> getGroup(@PathVariable UUID groupId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterGroupResponse.from(groupService.getGroup(groupId)));
    }

    @PostMapping
    public ResponseEntity<CenterGroupResponse> createGroup(@Valid @RequestBody CreateCenterGroupRequest request) {
        CenterGroupService.GroupView created = groupService.createGroup(
                request.academicYearId(),
                request.gradeLevelId(),
                request.subjectId(),
                request.defaultPackId(),
                request.code(),
                request.name(),
                request.description(),
                request.groupType(),
                request.roomName(),
                request.capacity(),
                request.startDate(),
                request.endDate());
        return ResponseEntity.created(URI.create("/api/v1/center/groups/" + created.group().getId()))
                .cacheControl(CacheControl.noStore())
                .body(CenterGroupResponse.from(created));
    }

    @PutMapping("/{groupId}")
    public ResponseEntity<CenterGroupResponse> updateGroup(
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateCenterGroupRequest request) {
        CenterGroupService.GroupView updated = groupService.updateGroup(
                groupId,
                request.gradeLevelId(),
                request.subjectId(),
                request.defaultPackId(),
                request.name(),
                request.description(),
                request.groupType(),
                request.roomName(),
                request.capacity(),
                request.startDate(),
                request.endDate());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterGroupResponse.from(updated));
    }

    @PostMapping("/{groupId}/activate")
    public ResponseEntity<CenterGroupResponse> activateGroup(@PathVariable UUID groupId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterGroupResponse.from(groupService.activateGroup(groupId)));
    }

    @PostMapping("/{groupId}/complete")
    public ResponseEntity<CenterGroupResponse> completeGroup(
            @PathVariable UUID groupId,
            @Valid @RequestBody(required = false) CenterReasonRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterGroupResponse.from(
                        groupService.completeGroup(groupId, request != null ? request.reason() : null)));
    }

    @PostMapping("/{groupId}/archive")
    public ResponseEntity<CenterGroupResponse> archiveGroup(
            @PathVariable UUID groupId,
            @Valid @RequestBody CenterReasonRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterGroupResponse.from(groupService.archiveGroup(groupId, request.reason())));
    }

    private static final Set<String> HOURLY_RATE_VIEW_AUTHORITIES = Set.of(
            "ROLE_PROMOTER",
            "ROLE_DIRECTOR",
            "ROLE_CENTER_MANAGER");

    // --- Intervenants ---

    @GetMapping("/{groupId}/tutors")
    public ResponseEntity<List<CenterGroupTutorResponse>> listTutors(@PathVariable UUID groupId) {
        List<CenterGroupTutorResponse> list = groupService.listGroupTutors(groupId).stream()
                .map(CenterGroupController::toMaskedTutorResponse)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(list);
    }

    @PostMapping("/{groupId}/tutors")
    public ResponseEntity<CenterGroupTutorResponse> assignTutor(
            @PathVariable UUID groupId,
            @Valid @RequestBody AssignCenterGroupTutorRequest request) {
        CenterGroupService.GroupTutorView created = groupService.assignTutor(
                groupId,
                request.teacherId(),
                request.subjectId(),
                Boolean.TRUE.equals(request.leadTutor()),
                request.hourlyRateXof(),
                request.startDate(),
                request.endDate(),
                request.notes());
        return ResponseEntity.created(
                        URI.create("/api/v1/center/groups/" + groupId + "/tutors/" + created.assignment().getId()))
                .cacheControl(CacheControl.noStore())
                .body(toMaskedTutorResponse(created));
    }

    @PostMapping("/{groupId}/tutors/{assignmentId}/deactivate")
    public ResponseEntity<CenterGroupTutorResponse> deactivateTutor(
            @PathVariable UUID groupId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody(required = false) DeactivateCenterTutorRequest request) {
        CenterGroupService.GroupTutorView updated = groupService.deactivateTutorAssignment(
                groupId,
                assignmentId,
                request != null ? request.endDate() : null,
                request != null ? request.reason() : null);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toMaskedTutorResponse(updated));
    }

    private static CenterGroupTutorResponse toMaskedTutorResponse(CenterGroupService.GroupTutorView view) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean includeHourlyRate = isSelfTutor(view, auth) || hasHourlyRateAuthority(auth);
        return CenterGroupTutorResponse.from(view, includeHourlyRate);
    }

    private static boolean isSelfTutor(CenterGroupService.GroupTutorView view, Authentication auth) {
        if (view == null
                || view.teacher() == null
                || view.teacher().userId() == null
                || auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null) {
            return false;
        }
        try {
            return view.teacher().userId().equals(UUID.fromString(auth.getName()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean hasHourlyRateAuthority(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (HOURLY_RATE_VIEW_AUTHORITIES.contains(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    // --- Inscriptions ---

    @GetMapping("/{groupId}/enrollments")
    public ResponseEntity<List<CenterGroupEnrollmentResponse>> listEnrollments(@PathVariable UUID groupId) {
        List<CenterGroupEnrollmentResponse> list = groupService.listGroupEnrollments(groupId).stream()
                .map(CenterGroupEnrollmentResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(list);
    }

    @PostMapping("/{groupId}/enrollments")
    public ResponseEntity<CenterGroupEnrollmentResponse> enrollStudent(
            @PathVariable UUID groupId,
            @Valid @RequestBody EnrollCenterStudentRequest request) {
        CenterGroupService.GroupEnrollmentView created = groupService.enrollStudent(
                groupId,
                request.studentId(),
                request.studentPackId(),
                request.enrolledAt(),
                request.notes());
        return ResponseEntity.created(
                        URI.create("/api/v1/center/groups/" + groupId + "/enrollments/" + created.enrollment().getId()))
                .cacheControl(CacheControl.noStore())
                .body(CenterGroupEnrollmentResponse.from(created));
    }

    @PostMapping("/{groupId}/enrollments/{enrollmentId}/status")
    public ResponseEntity<CenterGroupEnrollmentResponse> transitionEnrollment(
            @PathVariable UUID groupId,
            @PathVariable UUID enrollmentId,
            @Valid @RequestBody TransitionCenterEnrollmentRequest request) {
        CenterGroupService.GroupEnrollmentView updated = groupService.transitionEnrollmentStatus(
                groupId, enrollmentId, request.status(), request.endedAt(), request.reason());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CenterGroupEnrollmentResponse.from(updated));
    }
}
