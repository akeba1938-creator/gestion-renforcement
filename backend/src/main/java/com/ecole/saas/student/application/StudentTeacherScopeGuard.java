package com.ecole.saas.student.application;

import com.ecole.saas.student.domain.EnrollmentStatus;
import com.ecole.saas.student.domain.StudentAccessForbiddenException;
import com.ecole.saas.student.domain.StudentEnrollment;
import com.ecole.saas.student.infrastructure.StudentEnrollmentRepository;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Restreint finement les rôles {@code TEACHER} et {@code TUTOR} à leurs seules classes affectées
 * via {@link TeacherDirectoryPort}, sans impacter les rôles administratifs / direction.
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

    StudentTeacherScopeGuard(
            TeacherDirectoryPort teacherDirectoryPort,
            StudentEnrollmentRepository enrollmentRepository) {
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.enrollmentRepository = enrollmentRepository;
    }

    boolean isRestrictedTeacher() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        Set<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
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
        Set<UUID> classRoomIds = resolveAssignedClassRoomIds();
        if (classRoomIds.isEmpty()) {
            return Set.of();
        }
        return enrollmentRepository.findByClassRoomIdInAndStatus(classRoomIds, EnrollmentStatus.ACTIVE).stream()
                .map(StudentEnrollment::getStudentId)
                .collect(Collectors.toUnmodifiableSet());
    }

    void assertCanReadStudent(UUID studentId) {
        if (!isRestrictedTeacher()) {
            return;
        }
        Set<UUID> classRoomIds = resolveAssignedClassRoomIds();
        if (classRoomIds.isEmpty()
                || !enrollmentRepository.existsByStudentIdAndClassRoomIdInAndStatus(
                        studentId, classRoomIds, EnrollmentStatus.ACTIVE)) {
            throw new StudentAccessForbiddenException(
                    "Vous ne pouvez consulter que les élèves inscrits dans vos classes affectées.");
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
