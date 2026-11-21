package com.ecole.saas.schoolimport.application;

import com.ecole.saas.schoolimport.ImportType;
import com.ecole.saas.schoolimport.domain.ImportJob;
import com.ecole.saas.schoolimport.domain.SchoolImportForbiddenException;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Garde RBAC fin et restriction de périmètre pour {@code schoolimport} :
 * contrôle les types d'import autorisés par rôle et restreint strictement {@code TEACHER}/{@code TUTOR}
 * à leurs propres lots ainsi qu'à leurs classes/matières affectées.
 */
@Component
class SchoolImportAccessGuard {

    private static final Set<ImportType> SECRETARY_ALLOWED = Set.of(
            ImportType.STUDENTS, ImportType.FAMILIES_GUARDIANS, ImportType.ENROLLMENTS);
    private static final Set<ImportType> CENSOR_ALLOWED = Set.of(
            ImportType.TEACHERS, ImportType.GRADES, ImportType.ATTENDANCE);
    private static final Set<ImportType> TEACHER_ALLOWED = Set.of(
            ImportType.GRADES, ImportType.ATTENDANCE);

    private final TeacherDirectoryPort teacherDirectoryPort;

    SchoolImportAccessGuard(TeacherDirectoryPort teacherDirectoryPort) {
        this.teacherDirectoryPort = teacherDirectoryPort;
    }

    boolean isRestrictedTeacher() {
        Set<String> roles = currentRoles();
        boolean hasTeacherRole = roles.contains("ROLE_TEACHER") || roles.contains("ROLE_TUTOR");
        boolean hasAdminRole = roles.contains("ROLE_PROMOTER")
                || roles.contains("ROLE_DIRECTOR")
                || roles.contains("ROLE_CENTER_MANAGER")
                || roles.contains("ROLE_SECRETARY")
                || roles.contains("ROLE_CENSOR");
        return hasTeacherRole && !hasAdminRole;
    }

    void assertCanWriteImportType(ImportType importType) {
        Set<String> roles = currentRoles();
        if (roles.contains("ROLE_DIRECTOR") || roles.contains("ROLE_CENTER_MANAGER")) {
            return;
        }
        if (roles.contains("ROLE_SECRETARY") && SECRETARY_ALLOWED.contains(importType)) {
            return;
        }
        if (roles.contains("ROLE_CENSOR") && CENSOR_ALLOWED.contains(importType)) {
            return;
        }
        if ((roles.contains("ROLE_TEACHER") || roles.contains("ROLE_TUTOR")) && TEACHER_ALLOWED.contains(importType)) {
            return;
        }
        throw new SchoolImportForbiddenException(
                "Votre rôle ne vous autorise pas à effectuer un import de type " + importType.name() + ".");
    }

    void assertCanReadTemplate(ImportType importType) {
        if (isRestrictedTeacher() && !TEACHER_ALLOWED.contains(importType)) {
            throw new SchoolImportForbiddenException(
                    "Un enseignant ou intervenant ne peut consulter que les modèles d'import de notes et de présences.");
        }
    }

    void assertCanReadJob(ImportJob job, UUID actorId) {
        if (isRestrictedTeacher() && !job.getCreatedBy().equals(actorId)) {
            throw new SchoolImportForbiddenException(
                    "Vous ne pouvez consulter que vos propres lots d'import.");
        }
    }

    void assertCanMutateJob(ImportJob job, UUID actorId) {
        assertCanWriteImportType(job.getImportType());
        if (isRestrictedTeacher() && !job.getCreatedBy().equals(actorId)) {
            throw new SchoolImportForbiddenException(
                    "Vous ne pouvez exécuter ou annuler que vos propres lots d'import.");
        }
    }

    void assertTeacherScopeForGrades(UUID actorId, UUID classRoomId, UUID subjectId) {
        if (!isRestrictedTeacher()) {
            return;
        }
        if (!teacherDirectoryPort.isUserAssignedToClassSubject(actorId, classRoomId, subjectId)) {
            throw new SchoolImportForbiddenException(
                    "Vous ne pouvez importer des notes que pour les classes et matières qui vous sont affectées.");
        }
    }

    void assertTeacherScopeForAttendance(UUID actorId, UUID classRoomId, UUID subjectId) {
        if (!isRestrictedTeacher()) {
            return;
        }
        boolean allowed = subjectId != null
                ? teacherDirectoryPort.isUserAssignedToClassSubject(actorId, classRoomId, subjectId)
                : teacherDirectoryPort.isUserAssignedToClass(actorId, classRoomId);
        if (!allowed) {
            throw new SchoolImportForbiddenException(
                    "Vous ne pouvez importer des présences que pour les classes qui vous sont affectées.");
        }
    }

    private static Set<String> currentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
