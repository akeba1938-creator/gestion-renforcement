package com.ecole.saas.schooldocs.application;

import com.ecole.saas.family.FamilyDirectoryPort;
import com.ecole.saas.family.GuardianContactSummary;
import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schooldocs.domain.IssuedDocument;
import com.ecole.saas.schooldocs.domain.SchoolDocForbiddenException;
import com.ecole.saas.student.StudentContactSummary;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import java.util.EnumSet;
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
 * Garde de contrôle fin RBAC et de périmètre (élève, tuteur/parent, classes affectées enseignant/intervenant)
 * évalué exclusivement côté serveur à partir des rôles et du sujet JWT authentifié.
 */
@Component
class SchoolDocsAccessScopeGuard {

    private static final Set<String> TEMPLATE_MANAGER_ROLES = Set.of(
            "ROLE_PROMOTER",
            "ROLE_DIRECTOR",
            "ROLE_CENTER_MANAGER",
            "ROLE_SECRETARY");

    private static final Set<String> FULL_DOCUMENT_READ_ROLES = Set.of(
            "ROLE_PROMOTER",
            "ROLE_DIRECTOR",
            "ROLE_CENTER_MANAGER",
            "ROLE_SECRETARY",
            "ROLE_CENSOR",
            "ROLE_CASHIER");

    private static final Set<String> FULL_ISSUER_ROLES = Set.of(
            "ROLE_DIRECTOR",
            "ROLE_CENTER_MANAGER",
            "ROLE_SECRETARY");

    private static final Set<DocumentType> CENSOR_ALLOWED_DOCUMENT_TYPES = EnumSet.of(
            DocumentType.ATTENDANCE_ATTESTATION,
            DocumentType.SUCCESS_ATTESTATION,
            DocumentType.SUMMONS);

    private final TeacherDirectoryPort teacherDirectoryPort;
    private final StudentDirectoryPort studentDirectoryPort;
    private final FamilyDirectoryPort familyDirectoryPort;

    SchoolDocsAccessScopeGuard(
            TeacherDirectoryPort teacherDirectoryPort,
            StudentDirectoryPort studentDirectoryPort,
            FamilyDirectoryPort familyDirectoryPort) {
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.studentDirectoryPort = studentDirectoryPort;
        this.familyDirectoryPort = familyDirectoryPort;
    }

    boolean isTemplateManager() {
        return hasAnyAuthority(TEMPLATE_MANAGER_ROLES);
    }

    void assertCanIssueDocumentType(DocumentType documentType) {
        if (hasAnyAuthority(FULL_ISSUER_ROLES)) {
            return;
        }
        if (hasAuthority("ROLE_CENSOR")) {
            if (!CENSOR_ALLOWED_DOCUMENT_TYPES.contains(documentType)) {
                throw new SchoolDocForbiddenException(
                        "schooldocs-censor-type-forbidden",
                        "Le rôle CENSEUR est limité à l'émission des convocations et attestations d'assiduité ou de réussite.");
            }
            return;
        }
        throw new SchoolDocForbiddenException(
                "schooldocs-issue-forbidden",
                "Vous ne disposez pas des droits nécessaires pour émettre ce document officiel.");
    }

    CallerReadScope resolveReadScope(UUID actorUserId) {
        if (hasAnyAuthority(FULL_DOCUMENT_READ_ROLES)) {
            return CallerReadScope.fullSchool();
        }
        if (actorUserId == null) {
            return CallerReadScope.empty();
        }
        if (hasAuthority("ROLE_TEACHER") || hasAuthority("ROLE_TUTOR")) {
            Set<UUID> assignedClassRoomIds = teacherDirectoryPort.findAssignedClassRoomIdsByUserId(actorUserId);
            if (assignedClassRoomIds.isEmpty()) {
                return CallerReadScope.teacherScope(Set.of(), Set.of());
            }
            Set<UUID> enrolledStudentIds = studentDirectoryPort.findActiveStudentIdsByClassRooms(assignedClassRoomIds);
            return CallerReadScope.teacherScope(Set.copyOf(assignedClassRoomIds), Set.copyOf(enrolledStudentIds));
        }
        if (hasAuthority("ROLE_PARENT")) {
            Optional<GuardianContactSummary> guardianOpt =
                    familyDirectoryPort.findGuardianContactByUserId(actorUserId);
            if (guardianOpt.isEmpty() || !guardianOpt.get().active()) {
                return CallerReadScope.parentScope(null, Set.of());
            }
            UUID guardianId = guardianOpt.get().id();
            List<UUID> studentIds = familyDirectoryPort.findStudentIdsByGuardianId(guardianId);
            return CallerReadScope.parentScope(guardianId, Set.copyOf(studentIds));
        }
        if (hasAuthority("ROLE_STUDENT")) {
            Optional<StudentContactSummary> studentOpt =
                    studentDirectoryPort.findStudentContactByUserId(actorUserId);
            if (studentOpt.isEmpty()) {
                return CallerReadScope.studentScope(Set.of());
            }
            return CallerReadScope.studentScope(Set.of(studentOpt.get().id()));
        }
        return CallerReadScope.empty();
    }

    void assertCanAccessDocument(CallerReadScope scope, IssuedDocument document) {
        if (scope.mode() == ScopeMode.FULL_SCHOOL) {
            return;
        }
        boolean allowed = switch (scope.mode()) {
            case FULL_SCHOOL -> true;
            case TEACHER_CLASSES ->
                (document.getClassRoomId() != null && scope.classRoomIds().contains(document.getClassRoomId()))
                        || scope.studentIds().contains(document.getStudentId());
            case PARENT_STUDENTS ->
                (scope.guardianId() != null && scope.guardianId().equals(document.getGuardianId()))
                        || scope.studentIds().contains(document.getStudentId());
            case SELF_STUDENT -> scope.studentIds().contains(document.getStudentId());
            case EMPTY -> false;
        };
        if (!allowed) {
            throw new SchoolDocForbiddenException(
                    "schooldocs-scope-forbidden",
                    "Vous n'êtes pas autorisé à accéder à ce document scolaire hors de votre périmètre.");
        }
    }

    void assertFilterAllowed(
            CallerReadScope scope,
            UUID studentIdFilter,
            UUID guardianIdFilter,
            UUID classRoomIdFilter) {
        if (scope.mode() == ScopeMode.FULL_SCHOOL) {
            return;
        }
        if (studentIdFilter != null && !scope.studentIds().contains(studentIdFilter)) {
            throw new SchoolDocForbiddenException(
                    "schooldocs-scope-forbidden",
                    "Vous n'êtes pas autorisé à consulter les documents de cet élève.");
        }
        if (guardianIdFilter != null) {
            if (scope.mode() != ScopeMode.PARENT_STUDENTS
                    || scope.guardianId() == null
                    || !scope.guardianId().equals(guardianIdFilter)) {
                throw new SchoolDocForbiddenException(
                        "schooldocs-scope-forbidden",
                        "Vous n'êtes pas autorisé à filtrer sur ce tuteur.");
            }
        }
        if (classRoomIdFilter != null) {
            if (scope.mode() == ScopeMode.TEACHER_CLASSES
                    && !scope.classRoomIds().contains(classRoomIdFilter)) {
                throw new SchoolDocForbiddenException(
                        "schooldocs-scope-forbidden",
                        "Vous n'êtes pas autorisé à consulter les documents de cette classe.");
            }
        }
    }

    private static Set<String> currentAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    private static boolean hasAuthority(String authority) {
        return currentAuthorities().contains(authority);
    }

    private static boolean hasAnyAuthority(Set<String> expected) {
        Set<String> authorities = currentAuthorities();
        for (String role : expected) {
            if (authorities.contains(role)) {
                return true;
            }
        }
        return false;
    }

    enum ScopeMode {
        FULL_SCHOOL,
        TEACHER_CLASSES,
        PARENT_STUDENTS,
        SELF_STUDENT,
        EMPTY
    }

    record CallerReadScope(
            ScopeMode mode,
            Set<UUID> classRoomIds,
            Set<UUID> studentIds,
            UUID guardianId) {

        static CallerReadScope fullSchool() {
            return new CallerReadScope(ScopeMode.FULL_SCHOOL, Set.of(), Set.of(), null);
        }

        static CallerReadScope teacherScope(Set<UUID> classRoomIds, Set<UUID> studentIds) {
            return new CallerReadScope(ScopeMode.TEACHER_CLASSES, classRoomIds, studentIds, null);
        }

        static CallerReadScope parentScope(UUID guardianId, Set<UUID> studentIds) {
            return new CallerReadScope(ScopeMode.PARENT_STUDENTS, Set.of(), studentIds, guardianId);
        }

        static CallerReadScope studentScope(Set<UUID> studentIds) {
            return new CallerReadScope(ScopeMode.SELF_STUDENT, Set.of(), studentIds, null);
        }

        static CallerReadScope empty() {
            return new CallerReadScope(ScopeMode.EMPTY, Set.of(), Set.of(), null);
        }

        boolean isEmptyScopedResult() {
            return switch (mode) {
                case FULL_SCHOOL -> false;
                case TEACHER_CLASSES -> classRoomIds.isEmpty() && studentIds.isEmpty();
                case PARENT_STUDENTS -> guardianId == null && studentIds.isEmpty();
                case SELF_STUDENT -> studentIds.isEmpty();
                case EMPTY -> true;
            };
        }
    }
}
