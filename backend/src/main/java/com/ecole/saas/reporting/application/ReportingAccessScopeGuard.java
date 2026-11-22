package com.ecole.saas.reporting.application;

import com.ecole.saas.center.CenterDirectoryPort;
import com.ecole.saas.family.FamilyDirectoryPort;
import com.ecole.saas.family.GuardianContactSummary;
import com.ecole.saas.reporting.ReportType;
import com.ecole.saas.reporting.domain.ReportSnapshot;
import com.ecole.saas.reporting.domain.ReportingForbiddenException;
import com.ecole.saas.student.StudentContactSummary;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import com.ecole.saas.teacher.TeacherSummary;
import java.util.EnumSet;
import java.util.HashSet;
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
 * Garde de contrôle RBAC strict et de filtrage fin par périmètre ({@code TEACHER}/{@code TUTOR},
 * {@code PARENT}, {@code STUDENT}) évalué exclusivement côté serveur à partir du JWT authentifié.
 */
@Component
class ReportingAccessScopeGuard {

    private static final Set<String> EXECUTIVE_ROLES = Set.of(
            "ROLE_PROMOTER",
            "ROLE_DIRECTOR",
            "ROLE_CENTER_MANAGER");

    private static final Set<String> PEDAGOGY_STAFF_ROLES = Set.of(
            "ROLE_CENSOR",
            "ROLE_SECRETARY");

    private static final Set<String> FINANCE_VISIBLE_ROLES = Set.of(
            "ROLE_PROMOTER",
            "ROLE_DIRECTOR",
            "ROLE_CENTER_MANAGER",
            "ROLE_CASHIER",
            "ROLE_SECRETARY",
            "ROLE_PARENT",
            "ROLE_STUDENT");

    private final TeacherDirectoryPort teacherDirectoryPort;
    private final CenterDirectoryPort centerDirectoryPort;
    private final StudentDirectoryPort studentDirectoryPort;
    private final FamilyDirectoryPort familyDirectoryPort;

    ReportingAccessScopeGuard(
            TeacherDirectoryPort teacherDirectoryPort,
            CenterDirectoryPort centerDirectoryPort,
            StudentDirectoryPort studentDirectoryPort,
            FamilyDirectoryPort familyDirectoryPort) {
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.centerDirectoryPort = centerDirectoryPort;
        this.studentDirectoryPort = studentDirectoryPort;
        this.familyDirectoryPort = familyDirectoryPort;
    }

    CallerReportingScope resolveScope(UUID actorUserId) {
        Set<String> authorities = currentAuthorities();
        if (containsAny(authorities, EXECUTIVE_ROLES)) {
            return new CallerReportingScope(
                    ScopeMode.FULL_SCHOOL,
                    EnumSet.allOf(ReportType.class),
                    Set.of(),
                    Set.of(),
                    true,
                    false);
        }

        Set<ReportType> allowedTypes = EnumSet.noneOf(ReportType.class);
        boolean fullSchoolStaff = false;
        if (containsAny(authorities, PEDAGOGY_STAFF_ROLES)) {
            allowedTypes.add(ReportType.ACADEMIC);
            allowedTypes.add(ReportType.ATTENDANCE);
            allowedTypes.add(ReportType.OPERATIONS);
            allowedTypes.add(ReportType.STUDENT_OVERVIEW);
            fullSchoolStaff = true;
        }
        if (authorities.contains("ROLE_CASHIER")) {
            allowedTypes.add(ReportType.FINANCIAL);
            allowedTypes.add(ReportType.STUDENT_OVERVIEW);
            fullSchoolStaff = true;
        }
        if (fullSchoolStaff) {
            return new CallerReportingScope(
                    ScopeMode.FULL_SCHOOL,
                    Set.copyOf(allowedTypes),
                    Set.of(),
                    Set.of(),
                    containsAny(authorities, FINANCE_VISIBLE_ROLES),
                    false);
        }

        if (actorUserId == null) {
            return CallerReportingScope.empty();
        }

        if (authorities.contains("ROLE_TEACHER") || authorities.contains("ROLE_TUTOR")) {
            Set<UUID> assignedClassRoomIds = teacherDirectoryPort.findAssignedClassRoomIdsByUserId(actorUserId);
            Set<UUID> enrolledStudentIds = new HashSet<>();
            if (!assignedClassRoomIds.isEmpty()) {
                enrolledStudentIds.addAll(studentDirectoryPort.findActiveStudentIdsByClassRooms(assignedClassRoomIds));
            }
            if (authorities.contains("ROLE_TUTOR")) {
                Optional<TeacherSummary> teacherOpt = teacherDirectoryPort.findTeacherByUserId(actorUserId);
                if (teacherOpt.isPresent()) {
                    Set<UUID> groupIds = centerDirectoryPort.findAssignedGroupIdsByTeacherId(teacherOpt.get().id());
                    if (!groupIds.isEmpty()) {
                        enrolledStudentIds.addAll(centerDirectoryPort.findActiveStudentIdsByGroupIds(groupIds));
                    }
                }
            }
            return new CallerReportingScope(
                    ScopeMode.TEACHER_CLASSES,
                    EnumSet.of(ReportType.ACADEMIC, ReportType.ATTENDANCE, ReportType.STUDENT_OVERVIEW),
                    Set.copyOf(assignedClassRoomIds),
                    Set.copyOf(enrolledStudentIds),
                    false,
                    true);
        }

        if (authorities.contains("ROLE_PARENT")) {
            Optional<GuardianContactSummary> guardianOpt =
                    familyDirectoryPort.findGuardianContactByUserId(actorUserId);
            if (guardianOpt.isEmpty() || !guardianOpt.get().active()) {
                return new CallerReportingScope(
                        ScopeMode.PARENT_STUDENTS,
                        EnumSet.of(ReportType.STUDENT_OVERVIEW),
                        Set.of(),
                        Set.of(),
                        true,
                        true);
            }
            List<UUID> studentIds = familyDirectoryPort.findStudentIdsByGuardianId(guardianOpt.get().id());
            return new CallerReportingScope(
                    ScopeMode.PARENT_STUDENTS,
                    EnumSet.of(ReportType.STUDENT_OVERVIEW),
                    Set.of(),
                    Set.copyOf(studentIds),
                    true,
                    true);
        }

        if (authorities.contains("ROLE_STUDENT")) {
            Optional<StudentContactSummary> studentOpt =
                    studentDirectoryPort.findStudentContactByUserId(actorUserId);
            Set<UUID> selfIds = studentOpt.map(s -> Set.of(s.id())).orElse(Set.of());
            return new CallerReportingScope(
                    ScopeMode.SELF_STUDENT,
                    EnumSet.of(ReportType.STUDENT_OVERVIEW),
                    Set.of(),
                    selfIds,
                    true,
                    true);
        }

        return CallerReportingScope.empty();
    }

    void assertCanAccessReportType(CallerReportingScope scope, ReportType reportType) {
        if (!scope.allowedReportTypes().contains(reportType)) {
            throw new ReportingForbiddenException(
                    "reporting-role-forbidden",
                    "Votre rôle ne permet pas d'accéder aux rapports de type " + reportType + ".");
        }
    }

    void assertCanAccessClassRoom(CallerReportingScope scope, UUID classRoomId) {
        if (classRoomId == null || scope.mode() == ScopeMode.FULL_SCHOOL) {
            return;
        }
        if (scope.mode() == ScopeMode.TEACHER_CLASSES && scope.classRoomIds().contains(classRoomId)) {
            return;
        }
        throw new ReportingForbiddenException(
                "reporting-scope-forbidden",
                "Vous n'êtes pas autorisé à consulter les rapports de cette classe hors de votre périmètre.");
    }

    void assertCanAccessStudent(CallerReportingScope scope, UUID studentId) {
        if (scope.mode() == ScopeMode.FULL_SCHOOL) {
            return;
        }
        if (studentId != null && scope.studentIds().contains(studentId)) {
            return;
        }
        throw new ReportingForbiddenException(
                "reporting-scope-forbidden",
                "Vous n'êtes pas autorisé à consulter la synthèse de cet élève hors de votre périmètre.");
    }

    void assertCanAccessSnapshot(CallerReportingScope scope, UUID actorUserId, ReportSnapshot snapshot) {
        assertCanAccessReportType(scope, snapshot.getReportType());
        if (scope.ownSnapshotsOnly()) {
            if (actorUserId == null || !actorUserId.equals(snapshot.getGeneratedBy())) {
                throw new ReportingForbiddenException(
                        "reporting-scope-forbidden",
                        "Vous ne pouvez consulter que les snapshots de rapports que vous avez générés.");
            }
            if (snapshot.getClassRoomId() != null) {
                assertCanAccessClassRoom(scope, snapshot.getClassRoomId());
            }
            if (snapshot.getStudentId() != null) {
                assertCanAccessStudent(scope, snapshot.getStudentId());
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

    private static boolean containsAny(Set<String> authorities, Set<String> expected) {
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

    record CallerReportingScope(
            ScopeMode mode,
            Set<ReportType> allowedReportTypes,
            Set<UUID> classRoomIds,
            Set<UUID> studentIds,
            boolean canSeeStudentFinance,
            boolean ownSnapshotsOnly) {

        static CallerReportingScope empty() {
            return new CallerReportingScope(ScopeMode.EMPTY, Set.of(), Set.of(), Set.of(), false, true);
        }
    }
}
