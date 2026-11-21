package com.ecole.saas.communication.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.center.CenterDirectoryPort;
import com.ecole.saas.center.CenterGroupEnrollmentSummary;
import com.ecole.saas.communication.AudienceType;
import com.ecole.saas.communication.GuardianTargetingMode;
import com.ecole.saas.communication.domain.CommunicationNotFoundException;
import com.ecole.saas.communication.domain.RecipientType;
import com.ecole.saas.family.FamilyDirectoryPort;
import com.ecole.saas.family.GuardianContactSummary;
import com.ecole.saas.family.StudentGuardianSummary;
import com.ecole.saas.identity.UserContactSummary;
import com.ecole.saas.identity.UserDirectoryPort;
import com.ecole.saas.student.StudentContactSummary;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentEnrollmentSummary;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import com.ecole.saas.teacher.TeacherSummary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Résout une audience métier (unitaire ou groupée) en liste dédupliquée de destinataires concrets
 * en consommant exclusivement les ports publics Spring Modulith des autres modules.
 */
@Component
class AudienceResolver {

    private final AcademicStructurePort academicStructurePort;
    private final StudentDirectoryPort studentDirectoryPort;
    private final FamilyDirectoryPort familyDirectoryPort;
    private final TeacherDirectoryPort teacherDirectoryPort;
    private final CenterDirectoryPort centerDirectoryPort;
    private final UserDirectoryPort userDirectoryPort;

    AudienceResolver(
            AcademicStructurePort academicStructurePort,
            StudentDirectoryPort studentDirectoryPort,
            FamilyDirectoryPort familyDirectoryPort,
            TeacherDirectoryPort teacherDirectoryPort,
            CenterDirectoryPort centerDirectoryPort,
            UserDirectoryPort userDirectoryPort) {
        this.academicStructurePort = academicStructurePort;
        this.studentDirectoryPort = studentDirectoryPort;
        this.familyDirectoryPort = familyDirectoryPort;
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.centerDirectoryPort = centerDirectoryPort;
        this.userDirectoryPort = userDirectoryPort;
    }

    List<ResolvedRecipient> resolve(
            AudienceType audienceType,
            UUID targetId,
            GuardianTargetingMode guardianTargetingMode) {
        GuardianTargetingMode mode = guardianTargetingMode != null
                ? guardianTargetingMode
                : GuardianTargetingMode.ALL_ACTIVE;

        return switch (audienceType) {
            case GUARDIAN -> resolveSingleGuardian(targetId);
            case FAMILY -> resolveFamilyGuardians(targetId);
            case STUDENT -> resolveSingleStudent(targetId);
            case STUDENT_GUARDIANS -> resolveGuardiansOfStudents(List.of(requireStudentId(targetId)), mode);
            case CLASS_ROOM_GUARDIANS -> resolveClassRoomGuardians(targetId, mode);
            case CLASS_ROOM_STUDENTS -> resolveClassRoomStudents(targetId);
            case CENTER_GROUP_GUARDIANS -> resolveCenterGroupGuardians(targetId, mode);
            case CENTER_GROUP_STUDENTS -> resolveCenterGroupStudents(targetId);
            case TEACHER -> resolveSingleTeacher(targetId);
            case ALL_TEACHERS -> resolveAllTeachers();
            case STAFF_USER -> resolveSingleStaffUser(targetId);
            case ALL_STAFF -> resolveAllStaffUsers();
            case ALL_GUARDIANS -> resolveAllGuardians();
        };
    }

    void assertContactExists(RecipientType contactType, UUID contactId) {
        switch (contactType) {
            case GUARDIAN -> familyDirectoryPort.findGuardianById(contactId)
                    .orElseThrow(() -> new CommunicationNotFoundException("Parent ou tuteur introuvable."));
            case STUDENT -> studentDirectoryPort.findStudentById(contactId)
                    .orElseThrow(() -> new CommunicationNotFoundException("Élève introuvable."));
            case TEACHER -> teacherDirectoryPort.findTeacherById(contactId)
                    .orElseThrow(() -> new CommunicationNotFoundException("Enseignant ou intervenant introuvable."));
            case STAFF_USER -> userDirectoryPort.findUserById(contactId)
                    .orElseThrow(() -> new CommunicationNotFoundException("Compte utilisateur introuvable."));
        }
    }

    private List<ResolvedRecipient> resolveSingleGuardian(UUID guardianId) {
        GuardianContactSummary guardian = familyDirectoryPort.findGuardianContactById(guardianId)
                .orElseThrow(() -> new CommunicationNotFoundException("Parent ou tuteur introuvable."));
        return List.of(fromGuardian(guardian, null));
    }

    private List<ResolvedRecipient> resolveFamilyGuardians(UUID familyId) {
        familyDirectoryPort.findFamilyById(familyId)
                .orElseThrow(() -> new CommunicationNotFoundException("Famille introuvable."));
        return familyDirectoryPort.findActiveGuardiansByFamilyId(familyId).stream()
                .map(g -> fromGuardian(g, null))
                .toList();
    }

    private List<ResolvedRecipient> resolveSingleStudent(UUID studentId) {
        StudentContactSummary student = studentDirectoryPort.findStudentContactById(studentId)
                .orElseThrow(() -> new CommunicationNotFoundException("Élève introuvable."));
        return List.of(fromStudent(student));
    }

    private UUID requireStudentId(UUID studentId) {
        studentDirectoryPort.findStudentById(studentId)
                .orElseThrow(() -> new CommunicationNotFoundException("Élève introuvable."));
        return studentId;
    }

    private List<ResolvedRecipient> resolveClassRoomGuardians(UUID classRoomId, GuardianTargetingMode mode) {
        academicStructurePort.findClassRoomById(classRoomId)
                .orElseThrow(() -> new CommunicationNotFoundException("Classe introuvable."));
        List<UUID> studentIds = studentDirectoryPort.findActiveEnrollmentsByClassRoom(classRoomId).stream()
                .map(StudentEnrollmentSummary::studentId)
                .distinct()
                .toList();
        return resolveGuardiansOfStudents(studentIds, mode);
    }

    private List<ResolvedRecipient> resolveClassRoomStudents(UUID classRoomId) {
        academicStructurePort.findClassRoomById(classRoomId)
                .orElseThrow(() -> new CommunicationNotFoundException("Classe introuvable."));
        List<UUID> studentIds = studentDirectoryPort.findActiveEnrollmentsByClassRoom(classRoomId).stream()
                .map(StudentEnrollmentSummary::studentId)
                .distinct()
                .toList();
        return resolveStudentsByIds(studentIds);
    }

    private List<ResolvedRecipient> resolveCenterGroupGuardians(UUID groupId, GuardianTargetingMode mode) {
        centerDirectoryPort.findGroupById(groupId)
                .orElseThrow(() -> new CommunicationNotFoundException("Cohorte centre introuvable."));
        List<UUID> studentIds = centerDirectoryPort.findActiveEnrollmentsByGroup(groupId).stream()
                .map(CenterGroupEnrollmentSummary::studentId)
                .distinct()
                .toList();
        return resolveGuardiansOfStudents(studentIds, mode);
    }

    private List<ResolvedRecipient> resolveCenterGroupStudents(UUID groupId) {
        centerDirectoryPort.findGroupById(groupId)
                .orElseThrow(() -> new CommunicationNotFoundException("Cohorte centre introuvable."));
        List<UUID> studentIds = centerDirectoryPort.findActiveEnrollmentsByGroup(groupId).stream()
                .map(CenterGroupEnrollmentSummary::studentId)
                .distinct()
                .toList();
        return resolveStudentsByIds(studentIds);
    }

    private List<ResolvedRecipient> resolveSingleTeacher(UUID teacherId) {
        TeacherSummary teacher = teacherDirectoryPort.findTeacherById(teacherId)
                .orElseThrow(() -> new CommunicationNotFoundException("Enseignant ou intervenant introuvable."));
        return List.of(fromTeacher(teacher));
    }

    private List<ResolvedRecipient> resolveAllTeachers() {
        return teacherDirectoryPort.findAllActiveTeachers().stream()
                .map(AudienceResolver::fromTeacher)
                .toList();
    }

    private List<ResolvedRecipient> resolveSingleStaffUser(UUID userId) {
        UserContactSummary user = userDirectoryPort.findUserContactById(userId)
                .orElseThrow(() -> new CommunicationNotFoundException("Compte utilisateur introuvable."));
        return List.of(fromStaffUser(user));
    }

    private List<ResolvedRecipient> resolveAllStaffUsers() {
        return userDirectoryPort.findAllActiveStaffUsers().stream()
                .map(AudienceResolver::fromStaffUser)
                .toList();
    }

    private List<ResolvedRecipient> resolveAllGuardians() {
        return familyDirectoryPort.findAllActiveGuardians().stream()
                .map(g -> fromGuardian(g, null))
                .toList();
    }

    private List<ResolvedRecipient> resolveStudentsByIds(List<UUID> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, StudentContactSummary> byId = studentDirectoryPort.findStudentContactsByIds(studentIds).stream()
                .collect(Collectors.toMap(StudentContactSummary::id, Function.identity(), (a, b) -> a));
        List<ResolvedRecipient> results = new ArrayList<>();
        for (UUID studentId : studentIds) {
            StudentContactSummary contact = byId.get(studentId);
            if (contact != null) {
                results.add(fromStudent(contact));
            }
        }
        return results;
    }

    private List<ResolvedRecipient> resolveGuardiansOfStudents(
            List<UUID> studentIds,
            GuardianTargetingMode mode) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        List<StudentGuardianSummary> allLinks = familyDirectoryPort.findActiveGuardiansByStudentIds(studentIds);
        if (allLinks.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<StudentGuardianSummary>> linksByStudent = new LinkedHashMap<>();
        for (StudentGuardianSummary link : allLinks) {
            linksByStudent.computeIfAbsent(link.studentId(), ignored -> new ArrayList<>()).add(link);
        }

        // Déduplication stricte par guardianId (un parent avec plusieurs enfants dans la classe reçoit un seul message)
        Map<UUID, UUID> selectedGuardianToStudent = new LinkedHashMap<>();
        for (UUID studentId : studentIds) {
            List<StudentGuardianSummary> studentLinks = linksByStudent.getOrDefault(studentId, List.of());
            List<StudentGuardianSummary> chosen = filterByTargetingMode(studentLinks, mode);
            for (StudentGuardianSummary link : chosen) {
                selectedGuardianToStudent.putIfAbsent(link.guardianId(), studentId);
            }
        }
        if (selectedGuardianToStudent.isEmpty()) {
            return List.of();
        }

        Set<UUID> guardianIds = selectedGuardianToStudent.keySet();
        Map<UUID, GuardianContactSummary> contactsById =
                familyDirectoryPort.findGuardianContactsByIds(guardianIds).stream()
                        .collect(Collectors.toMap(GuardianContactSummary::id, Function.identity(), (a, b) -> a));

        List<ResolvedRecipient> recipients = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : selectedGuardianToStudent.entrySet()) {
            GuardianContactSummary contact = contactsById.get(entry.getKey());
            if (contact != null) {
                recipients.add(fromGuardian(contact, entry.getValue()));
            }
        }
        return recipients;
    }

    private static List<StudentGuardianSummary> filterByTargetingMode(
            List<StudentGuardianSummary> links,
            GuardianTargetingMode mode) {
        if (links.isEmpty()) {
            return List.of();
        }
        return switch (mode) {
            case ALL_ACTIVE -> links;
            case PRIMARY_ONLY -> {
                List<StudentGuardianSummary> primary = links.stream()
                        .filter(StudentGuardianSummary::primaryContact)
                        .toList();
                yield primary.isEmpty() ? List.of(links.getFirst()) : primary;
            }
            case FINANCIAL_RESPONSIBLE_ONLY -> {
                List<StudentGuardianSummary> financial = links.stream()
                        .filter(StudentGuardianSummary::financialResponsible)
                        .toList();
                if (!financial.isEmpty()) {
                    yield financial;
                }
                List<StudentGuardianSummary> primary = links.stream()
                        .filter(StudentGuardianSummary::primaryContact)
                        .toList();
                yield primary.isEmpty() ? List.of(links.getFirst()) : primary;
            }
        };
    }

    private static ResolvedRecipient fromGuardian(GuardianContactSummary g, UUID relatedStudentId) {
        return new ResolvedRecipient(
                RecipientType.GUARDIAN,
                g.id(),
                g.userId(),
                relatedStudentId,
                fullName(g.firstName(), g.lastName()),
                g.phone(),
                g.email(),
                g.active());
    }

    private static ResolvedRecipient fromStudent(StudentContactSummary s) {
        return new ResolvedRecipient(
                RecipientType.STUDENT,
                s.id(),
                s.userId(),
                s.id(),
                fullName(s.firstName(), s.lastName()),
                s.phone(),
                s.email(),
                s.active());
    }

    private static ResolvedRecipient fromTeacher(TeacherSummary t) {
        return new ResolvedRecipient(
                RecipientType.TEACHER,
                t.id(),
                t.userId(),
                null,
                fullName(t.firstName(), t.lastName()),
                t.phone(),
                t.email(),
                t.active());
    }

    private static ResolvedRecipient fromStaffUser(UserContactSummary u) {
        return new ResolvedRecipient(
                RecipientType.STAFF_USER,
                u.id(),
                u.id(),
                null,
                fullName(u.firstName(), u.lastName()),
                u.phone(),
                u.email(),
                u.active());
    }

    private static String fullName(String firstName, String lastName) {
        String first = firstName != null ? firstName.trim() : "";
        String last = lastName != null ? lastName.trim() : "";
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? "Destinataire" : combined;
    }

    record ResolvedRecipient(
            RecipientType recipientType,
            UUID recipientId,
            UUID recipientUserId,
            UUID relatedStudentId,
            String displayName,
            String phone,
            String email,
            boolean active) {
    }
}
