package com.ecole.saas.center.application;

import com.ecole.saas.center.domain.CenterForbiddenException;
import com.ecole.saas.center.domain.CenterGroupTutor;
import com.ecole.saas.center.domain.CenterSession;
import com.ecole.saas.center.infrastructure.CenterGroupTutorRepository;
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
 * Restreint finement les rôles {@code TEACHER} et {@code TUTOR} à leurs seules cohortes et séances
 * affectées en mode centre, à partir du sujet JWT côté serveur.
 */
@Component
class CenterTutorScopeGuard {

    private static final Set<String> UNRESTRICTED_STAFF_AUTHORITIES = Set.of(
            "ROLE_PROMOTER",
            "ROLE_DIRECTOR",
            "ROLE_CENTER_MANAGER",
            "ROLE_CENSOR",
            "ROLE_SECRETARY",
            "ROLE_CASHIER");

    private static final Set<String> TUTOR_AUTHORITIES = Set.of("ROLE_TEACHER", "ROLE_TUTOR");

    private final TeacherDirectoryPort teacherDirectoryPort;
    private final CenterGroupTutorRepository groupTutorRepository;

    CenterTutorScopeGuard(
            TeacherDirectoryPort teacherDirectoryPort,
            CenterGroupTutorRepository groupTutorRepository) {
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.groupTutorRepository = groupTutorRepository;
    }

    boolean isRestrictedTutor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        Set<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        boolean hasStaff = authorities.stream().anyMatch(UNRESTRICTED_STAFF_AUTHORITIES::contains);
        boolean hasTutor = authorities.stream().anyMatch(TUTOR_AUTHORITIES::contains);
        return hasTutor && !hasStaff;
    }

    Optional<TeacherSummary> currentTutorProfile() {
        UUID userId = requireCurrentUserId();
        return teacherDirectoryPort.findTeacherByUserId(userId);
    }

    UUID requireCurrentTutorTeacherId() {
        return currentTutorProfile()
                .map(TeacherSummary::id)
                .orElseThrow(() -> CenterForbiddenException.tutorScope(
                        "Votre compte utilisateur n'est associé à aucun dossier intervenant actif."));
    }

    Set<UUID> currentTutorGroupIds() {
        Optional<TeacherSummary> teacher = currentTutorProfile();
        if (teacher.isEmpty()) {
            return Set.of();
        }
        return groupTutorRepository.findByTeacherIdAndActiveTrue(teacher.get().id()).stream()
                .map(CenterGroupTutor::getGroupId)
                .collect(Collectors.toSet());
    }

    void assertCanAccessGroup(UUID groupId) {
        if (!isRestrictedTutor()) {
            return;
        }
        UUID teacherId = requireCurrentTutorTeacherId();
        if (!groupTutorRepository.existsByGroupIdAndTeacherIdAndActiveTrue(groupId, teacherId)) {
            throw CenterForbiddenException.tutorScope(
                    "Vous n'êtes pas affecté à cette cohorte de renforcement.");
        }
    }

    void assertCanAccessSession(CenterSession session) {
        if (!isRestrictedTutor()) {
            return;
        }
        UUID teacherId = requireCurrentTutorTeacherId();
        boolean directlyAssigned = teacherId.equals(session.getTeacherId());
        boolean assignedToGroup =
                groupTutorRepository.existsByGroupIdAndTeacherIdAndActiveTrue(session.getGroupId(), teacherId);
        if (!directlyAssigned && !assignedToGroup) {
            throw CenterForbiddenException.tutorScope(
                    "Vous n'êtes pas affecté à cette séance ou à sa cohorte.");
        }
    }

    void assertCanAccessTeacher(UUID teacherId) {
        if (!isRestrictedTutor()) {
            return;
        }
        UUID currentTeacherId = requireCurrentTutorTeacherId();
        if (!currentTeacherId.equals(teacherId)) {
            throw CenterForbiddenException.tutorScope(
                    "Vous ne pouvez consulter que votre propre charge d'intervention.");
        }
    }

    void assertCanAccessStudentInGroups(Set<UUID> studentActiveGroupIds) {
        if (!isRestrictedTutor()) {
            return;
        }
        Set<UUID> myGroupIds = currentTutorGroupIds();
        boolean sharesGroup = studentActiveGroupIds.stream().anyMatch(myGroupIds::contains);
        if (!sharesGroup) {
            throw CenterForbiddenException.tutorScope(
                    "Cet élève n'est inscrit dans aucune de vos cohortes affectées.");
        }
    }

    private static UUID requireCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw CenterForbiddenException.tutorScope("Identifiant d'utilisateur authentifié introuvable.");
        }
        return UUID.fromString(auth.getName());
    }
}
