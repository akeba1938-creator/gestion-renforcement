package com.ecole.saas.attendance.application;

import com.ecole.saas.attendance.domain.AttendanceForbiddenException;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import com.ecole.saas.teacher.TeacherSummary;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Restreint finement les rôles {@code TEACHER} et {@code TUTOR} à leurs seules classes
 * (et matières si la séance d'appel est rattachée à une matière) via {@link TeacherDirectoryPort}.
 */
@Component
class AttendanceTeacherScopeGuard {

    private static final Set<String> UNRESTRICTED_READ_AUTHORITIES = Set.of(
            "ROLE_PROMOTER",
            "ROLE_DIRECTOR",
            "ROLE_CENTER_MANAGER",
            "ROLE_CENSOR",
            "ROLE_SECRETARY");

    private static final Set<String> TEACHER_AUTHORITIES = Set.of("ROLE_TEACHER", "ROLE_TUTOR");

    private final TeacherDirectoryPort teacherDirectoryPort;

    AttendanceTeacherScopeGuard(TeacherDirectoryPort teacherDirectoryPort) {
        this.teacherDirectoryPort = teacherDirectoryPort;
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

    Set<UUID> currentTeacherClassRoomIds() {
        UUID userId = requireCurrentUserId();
        return teacherDirectoryPort.findAssignedClassRoomIdsByUserId(userId);
    }

    Optional<TeacherSummary> currentTeacherProfile() {
        UUID userId = requireCurrentUserId();
        return teacherDirectoryPort.findTeacherByUserId(userId);
    }

    void assertCanAccessClass(UUID classRoomId) {
        if (!isRestrictedTeacher()) {
            return;
        }
        UUID userId = requireCurrentUserId();
        if (!teacherDirectoryPort.isUserAssignedToClass(userId, classRoomId)) {
            throw new AttendanceForbiddenException(
                    "Vous n'êtes pas affecté à cette classe pour gérer ou consulter l'assiduité.");
        }
    }

    void assertCanAccessSessionScope(UUID classRoomId, UUID subjectId) {
        if (!isRestrictedTeacher()) {
            return;
        }
        UUID userId = requireCurrentUserId();
        if (subjectId != null) {
            if (!teacherDirectoryPort.isUserAssignedToClassSubject(userId, classRoomId, subjectId)) {
                throw new AttendanceForbiddenException(
                        "Vous n'êtes pas affecté à cette classe pour cette matière.");
            }
        } else if (!teacherDirectoryPort.isUserAssignedToClass(userId, classRoomId)) {
            throw new AttendanceForbiddenException(
                    "Vous n'êtes pas affecté à cette classe pour réaliser l'appel.");
        }
    }

    private static UUID requireCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new AttendanceForbiddenException("Identifiant d'utilisateur authentifié introuvable.");
        }
        return UUID.fromString(auth.getName());
    }
}
