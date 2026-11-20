package com.ecole.saas.grade.application;

import com.ecole.saas.grade.domain.GradeForbiddenException;
import com.ecole.saas.teacher.TeacherAssignmentSummary;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import com.ecole.saas.teacher.TeacherSummary;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Restreint finement les rôles {@code TEACHER} et {@code TUTOR} à leurs seules classes et matières
 * affectées via {@link TeacherDirectoryPort}.
 */
@Component
class GradeTeacherScopeGuard {

    private static final Set<String> UNRESTRICTED_READ_AUTHORITIES = Set.of(
            "ROLE_PROMOTER",
            "ROLE_DIRECTOR",
            "ROLE_CENTER_MANAGER",
            "ROLE_CENSOR",
            "ROLE_SECRETARY");

    private static final Set<String> ADMIN_WRITE_AUTHORITIES = Set.of(
            "ROLE_DIRECTOR",
            "ROLE_CENTER_MANAGER",
            "ROLE_CENSOR");

    private static final Set<String> TEACHER_AUTHORITIES = Set.of("ROLE_TEACHER", "ROLE_TUTOR");

    private final TeacherDirectoryPort teacherDirectoryPort;

    GradeTeacherScopeGuard(TeacherDirectoryPort teacherDirectoryPort) {
        this.teacherDirectoryPort = teacherDirectoryPort;
    }

    boolean isRestrictedTeacher() {
        Set<String> authorities = currentAuthorities();
        boolean hasUnrestricted = authorities.stream().anyMatch(UNRESTRICTED_READ_AUTHORITIES::contains);
        boolean hasTeacher = authorities.stream().anyMatch(TEACHER_AUTHORITIES::contains);
        return hasTeacher && !hasUnrestricted;
    }

    boolean hasAdminWriteRole() {
        return currentAuthorities().stream().anyMatch(ADMIN_WRITE_AUTHORITIES::contains);
    }

    List<TeacherAssignmentSummary> currentTeacherAssignments() {
        UUID userId = requireCurrentUserId();
        return teacherDirectoryPort.findActiveAssignmentsByUserId(userId);
    }

    Set<UUID> currentTeacherClassRoomIds() {
        UUID userId = requireCurrentUserId();
        return teacherDirectoryPort.findAssignedClassRoomIdsByUserId(userId);
    }

    Optional<TeacherSummary> currentTeacherProfile() {
        UUID userId = requireCurrentUserId();
        return teacherDirectoryPort.findTeacherByUserId(userId);
    }

    void assertCanAccessClassSubject(UUID classRoomId, UUID subjectId) {
        if (!isRestrictedTeacher()) {
            return;
        }
        UUID userId = requireCurrentUserId();
        if (!teacherDirectoryPort.isUserAssignedToClassSubject(userId, classRoomId, subjectId)) {
            throw new GradeForbiddenException(
                    "Vous n'êtes pas affecté à cette classe et à cette matière.");
        }
    }

    void assertCanAccessClass(UUID classRoomId) {
        if (!isRestrictedTeacher()) {
            return;
        }
        UUID userId = requireCurrentUserId();
        if (!teacherDirectoryPort.isUserAssignedToClass(userId, classRoomId)) {
            throw new GradeForbiddenException(
                    "Vous n'êtes pas affecté à cette classe.");
        }
    }

    void assertCanWriteHomeroomAppreciation(UUID classRoomId) {
        if (hasAdminWriteRole()) {
            return;
        }
        UUID userId = requireCurrentUserId();
        if (!teacherDirectoryPort.isUserHomeroomTeacherOfClass(userId, classRoomId)) {
            throw new GradeForbiddenException(
                    "Seul le professeur principal de la classe ou la direction peut rédiger cette appréciation.");
        }
    }

    void assertCanWriteDirectorAppreciation() {
        if (!hasAdminWriteRole()) {
            throw new GradeForbiddenException(
                    "Seuls le directeur, le responsable de centre ou le censeur peuvent saisir l'appréciation de direction.");
        }
    }

    private static Set<String> currentAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    private static UUID requireCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new GradeForbiddenException("Identifiant d'utilisateur authentifié introuvable.");
        }
        return UUID.fromString(auth.getName());
    }
}
