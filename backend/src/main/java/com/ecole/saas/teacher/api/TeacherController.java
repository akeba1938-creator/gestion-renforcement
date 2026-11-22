package com.ecole.saas.teacher.api;

import com.ecole.saas.teacher.application.TeacherAssignmentService;
import com.ecole.saas.teacher.application.TeacherService;
import com.ecole.saas.teacher.domain.Teacher;
import com.ecole.saas.teacher.domain.TeacherStatus;
import com.ecole.saas.teacher.domain.TeacherType;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestion des dossiers enseignants / intervenants et de leurs spécialités (tenant issu exclusivement du JWT).
 */
@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherController {

    private static final Set<String> NATIONAL_ID_VIEW_AUTHORITIES = Set.of(
            "ROLE_DIRECTOR",
            "ROLE_CENTER_MANAGER",
            "ROLE_SECRETARY");

    private static final Set<String> HOURLY_RATE_VIEW_AUTHORITIES = Set.of(
            "ROLE_PROMOTER",
            "ROLE_DIRECTOR",
            "ROLE_CENTER_MANAGER");

    private final TeacherService teacherService;
    private final TeacherAssignmentService assignmentService;

    public TeacherController(TeacherService teacherService, TeacherAssignmentService assignmentService) {
        this.teacherService = teacherService;
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<TeacherResponse>> listTeachers(
            @RequestParam(required = false) TeacherStatus status,
            @RequestParam(required = false) TeacherType teacherType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastName") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = TeacherPagination.of(
                page, size, sortBy, direction, TeacherPagination.TEACHER_SORT_FIELDS, "lastName", Sort.Direction.ASC);
        Page<TeacherResponse> teachers = teacherService
                .listTeachers(status, teacherType, search, pageable)
                .map(TeacherController::toMaskedResponse);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(teachers));
    }

    @GetMapping("/me")
    public ResponseEntity<TeacherResponse> getOwnProfile(JwtAuthenticationToken authentication) {
        UUID userId = TeacherApiSupport.extractActorId(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(TeacherResponse.from(teacherService.getOwnTeacherProfile(userId), true, true));
    }

    @GetMapping("/me/assignments")
    public ResponseEntity<List<TeacherAssignmentResponse>> getOwnAssignments(
            @RequestParam(required = false) UUID academicYearId,
            JwtAuthenticationToken authentication) {
        UUID userId = TeacherApiSupport.extractActorId(authentication);
        List<TeacherAssignmentResponse> assignments = assignmentService
                .listOwnAssignments(userId, academicYearId)
                .stream()
                .map(TeacherAssignmentResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(assignments);
    }

    @GetMapping("/{teacherId}")
    public ResponseEntity<TeacherResponse> getTeacher(@PathVariable UUID teacherId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toMaskedResponse(teacherService.getTeacher(teacherId)));
    }

    @PostMapping
    public ResponseEntity<TeacherResponse> createTeacher(@Valid @RequestBody CreateTeacherRequest request) {
        Teacher created = teacherService.createTeacher(
                request.employeeCode(),
                request.userId(),
                request.firstName(),
                request.lastName(),
                request.gender(),
                request.teacherType(),
                request.phone(),
                request.secondaryPhone(),
                request.email(),
                request.address(),
                request.nationalIdNumber(),
                request.hireDate(),
                request.hourlyRateXof(),
                request.notes());
        return ResponseEntity.created(URI.create("/api/v1/teachers/" + created.getId()))
                .cacheControl(CacheControl.noStore())
                .body(toMaskedResponse(created));
    }

    @PutMapping("/{teacherId}")
    public ResponseEntity<TeacherResponse> updateTeacher(
            @PathVariable UUID teacherId,
            @Valid @RequestBody UpdateTeacherRequest request) {
        Teacher updated = teacherService.updateTeacher(
                teacherId,
                request.firstName(),
                request.lastName(),
                request.gender(),
                request.teacherType(),
                request.phone(),
                request.secondaryPhone(),
                request.email(),
                request.address(),
                request.nationalIdNumber(),
                request.hireDate(),
                request.hourlyRateXof(),
                request.notes());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toMaskedResponse(updated));
    }

    @PutMapping("/{teacherId}/user-link")
    public ResponseEntity<TeacherResponse> linkUserAccount(
            @PathVariable UUID teacherId,
            @Valid @RequestBody LinkTeacherUserRequest request) {
        Teacher updated = teacherService.linkUserAccount(teacherId, request != null ? request.userId() : null);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toMaskedResponse(updated));
    }

    @PostMapping("/{teacherId}/suspend")
    public ResponseEntity<TeacherResponse> suspendTeacher(
            @PathVariable UUID teacherId,
            @Valid @RequestBody TeacherStatusActionRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toMaskedResponse(teacherService.suspendTeacher(teacherId, request.reason())));
    }

    @PostMapping("/{teacherId}/reactivate")
    public ResponseEntity<TeacherResponse> reactivateTeacher(@PathVariable UUID teacherId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toMaskedResponse(teacherService.reactivateTeacher(teacherId)));
    }

    @PostMapping("/{teacherId}/archive")
    public ResponseEntity<TeacherResponse> archiveTeacher(
            @PathVariable UUID teacherId,
            @Valid @RequestBody TeacherStatusActionRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toMaskedResponse(teacherService.archiveTeacher(teacherId, request.reason())));
    }

    private static TeacherResponse toMaskedResponse(Teacher teacher) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isSelf = isSelf(teacher, auth);
        boolean includeNationalId = isSelf || hasAnyAuthority(auth, NATIONAL_ID_VIEW_AUTHORITIES);
        boolean includeHourlyRate = isSelf || hasAnyAuthority(auth, HOURLY_RATE_VIEW_AUTHORITIES);
        return TeacherResponse.from(teacher, includeNationalId, includeHourlyRate);
    }

    private static boolean isSelf(Teacher teacher, Authentication auth) {
        if (teacher.getUserId() == null || auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return false;
        }
        try {
            return teacher.getUserId().equals(UUID.fromString(auth.getName()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean hasAnyAuthority(Authentication auth, Set<String> expected) {
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (expected.contains(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    @GetMapping("/{teacherId}/specialties")
    public ResponseEntity<List<TeacherSpecialtyResponse>> listSpecialties(@PathVariable UUID teacherId) {
        List<TeacherSpecialtyResponse> specialties = teacherService.listSpecialties(teacherId).stream()
                .map(TeacherSpecialtyResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(specialties);
    }

    @PostMapping("/{teacherId}/specialties")
    public ResponseEntity<TeacherSpecialtyResponse> addSpecialty(
            @PathVariable UUID teacherId,
            @Valid @RequestBody AddTeacherSpecialtyRequest request) {
        TeacherService.SpecialtyView created = teacherService.addSpecialty(
                teacherId, request.subjectId(), Boolean.TRUE.equals(request.primarySpecialty()), request.notes());
        return ResponseEntity.created(
                        URI.create("/api/v1/teachers/" + teacherId + "/specialties/" + created.specialty().getSubjectId()))
                .cacheControl(CacheControl.noStore())
                .body(TeacherSpecialtyResponse.from(created));
    }

    @DeleteMapping("/{teacherId}/specialties/{subjectId}")
    public ResponseEntity<Void> removeSpecialty(@PathVariable UUID teacherId, @PathVariable UUID subjectId) {
        teacherService.removeSpecialty(teacherId, subjectId);
        return ResponseEntity.noContent().build();
    }
}
