package com.ecole.saas.student.application;

import com.ecole.saas.student.TutorCohortStudentScopeProvider;
import com.ecole.saas.student.domain.EnrollmentStatus;
import com.ecole.saas.student.domain.StudentAccessForbiddenException;
import com.ecole.saas.student.domain.StudentEnrollment;
import com.ecole.saas.student.infrastructure.StudentEnrollmentRepository;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Restreint finement les rôles {@code TEACHER} et {@code TUTOR} à leurs seules classes affectées
 * via {@link TeacherDirectoryPort} (et cohortes actives de centre via {@link TutorCohortStudentScopeProvider}
 * pour {@code TUTOR}), sans impacter les rôles administratifs / direction.
 */
@Component
class StudentTeacherScopeGuard {

    private static final Set<String> UNRESTRICTED_READ_AUTHORITIES = Set.of(
            "ROLE_PROMOTER",
            "ROLE_DIRECTOR",
            "ROLE_CENTER_MANAGER",
            "ROLE_SECRETARY",
            "ROLE_CASHIER",
            "ROLE_CENSOR");

    private static final Set<String> TEACHER_AUTHORITIES = Set.of("ROLE_TEACHER", "ROLE_TUTOR");

    private final TeacherDirectoryPort teacherDirectoryPort;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final List<TutorCohortStudentScopeProvider> tutorCohortScopeProviders;

    StudentTeacherScopeGuard(
            TeacherDirectoryPort teacherDirectoryPort,
            StudentEnrollmentRepository enrollmentRepository,
            List<TutorCohortStudentScopeProvider> tutorCohortScopeProviders) {
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.enrollmentRepository = enrollmentRepository;
        this.tutorCohortScopeProviders = tutorCohortScopeProviders != null ? tutorCohortScopeProviders : List.of();
    }

    boolean isRestrictedTeacher() {
        Set<String> authorities = currentAuthorities();
        if (authorities.isEmpty()) {
            return false;
        }
        boolean hasUnrestricted = authorities.stream().anyMatch(UNRESTRICTED_READ_AUTHORITIES::contains);
        boolean hasTeacher = authorities.stream().anyMatch(TEACHER_AUTHORITIES::contains);
        return hasTeacher && !hasUnrestricted;
    }

    Set<UUID> resolveAssignedClassRoomIds() {
        UUID userId = extractCurrentUserId();
        if (userId == null) {
            return Set.of();
        }
        return teacherDirectoryPort.findAssignedClassRoomIdsByUserId(userId);
    }

    Set<UUID> resolveVisibleStudentIds() {
        Set<UUID> visible = new HashSet<>();
        Set<UUID> classRoomIds = resolveAssignedClassRoomIds();
        if (!classRoomIds.isEmpty()) {
            enrollmentRepository.findByClassRoomIdInAndStatus(classRoomIds, EnrollmentStatus.ACTIVE).stream()
                    .map(StudentEnrollment::getStudentId)
                    .forEach(visible::add);
        }
        if (hasTutorAuthority()) {
            visible.addAll(resolveTutorCohortStudentIds());
        }
        return Set.copyOf(visible);
    }

    void assertCanReadStudent(UUID studentId) {
        if (!isRestrictedTeacher()) {
            return;
        }
        Set<UUID> classRoomIds = resolveAssignedClassRoomIds();
        boolean inAssignedClass = !classRoomIds.isEmpty()
                && enrollmentRepository.existsByStudentIdAndClassRoomIdInAndStatus(
                        studentId, classRoomIds, EnrollmentStatus.ACTIVE);
        boolean inTutorCohort = hasTutorAuthority() && resolveTutorCohortStudentIds().contains(studentId);
        if (!inAssignedClass && !inTutorCohort) {
            throw new StudentAccessForbiddenException(
                    "Vous ne pouvez consulter que les élèves inscrits dans vos classes ou cohortes affectées.");
        }
    }

    void assertCanReadClassRoom(UUID classRoomId) {
        if (!isRestrictedTeacher()) {
            return;
        }
        Set<UUID> classRoomIds = resolveAssignedClassRoomIds();
        if (!classRoomIds.contains(classRoomId)) {
            throw new StudentAccessForbiddenException(
                    "Vous ne pouvez consulter que les inscriptions de vos classes affectées.");
        }
    }

    private Set<UUID> resolveTutorCohortStudentIds() {
        UUID userId = extractCurrentUserId();
        if (userId == null || tutorCohortScopeProviders.isEmpty()) {
            return Set.of();
        }
        Set<UUID> result = new HashSet<>();
        for (TutorCohortStudentScopeProvider provider : tutorCohortScopeProviders) {
            Set<UUID> cohortStudents = provider.findActiveStudentIdsForTutorUser(userId);
            if (cohortStudents != null) {
                result.addAll(cohortStudents);
            }
        }
        return result;
    }

    private static boolean hasTutorAuthority() {
        return currentAuthorities().contains("ROLE_TUTOR");
    }

    private static Set<String> currentAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities() == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    private static UUID extractCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
