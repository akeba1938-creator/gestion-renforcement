package com.ecole.saas.center.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.AcademicYearSummary;
import com.ecole.saas.academic.GradeLevelSummary;
import com.ecole.saas.academic.SubjectSummary;
import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.center.domain.CenterConflictException;
import com.ecole.saas.center.domain.CenterGroup;
import com.ecole.saas.center.domain.CenterGroupEnrollment;
import com.ecole.saas.center.domain.CenterGroupEnrollmentStatus;
import com.ecole.saas.center.domain.CenterGroupStatus;
import com.ecole.saas.center.domain.CenterGroupTutor;
import com.ecole.saas.center.domain.CenterGroupType;
import com.ecole.saas.center.domain.CenterNotFoundException;
import com.ecole.saas.center.domain.CenterSessionPack;
import com.ecole.saas.center.domain.CenterStudentPack;
import com.ecole.saas.center.domain.CenterStudentPackStatus;
import com.ecole.saas.center.domain.CenterValidationException;
import com.ecole.saas.center.infrastructure.CenterGroupEnrollmentRepository;
import com.ecole.saas.center.infrastructure.CenterGroupRepository;
import com.ecole.saas.center.infrastructure.CenterGroupTutorRepository;
import com.ecole.saas.center.infrastructure.CenterLockRepository;
import com.ecole.saas.center.infrastructure.CenterSessionPackRepository;
import com.ecole.saas.center.infrastructure.CenterStudentPackRepository;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentSummary;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import com.ecole.saas.teacher.TeacherSummary;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des cohortes de renforcement, des affectations d'intervenants et des inscriptions élèves.
 */
@Service
public class CenterGroupService {

    private final CenterGroupRepository groupRepository;
    private final CenterGroupTutorRepository groupTutorRepository;
    private final CenterGroupEnrollmentRepository enrollmentRepository;
    private final CenterSessionPackRepository packRepository;
    private final CenterStudentPackRepository studentPackRepository;
    private final CenterLockRepository centerLockRepository;
    private final AcademicStructurePort academicStructurePort;
    private final TeacherDirectoryPort teacherDirectoryPort;
    private final StudentDirectoryPort studentDirectoryPort;
    private final CenterSubscriptionGuard subscriptionGuard;
    private final CenterSchoolGuard schoolGuard;
    private final CenterTutorScopeGuard tutorScopeGuard;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public CenterGroupService(
            CenterGroupRepository groupRepository,
            CenterGroupTutorRepository groupTutorRepository,
            CenterGroupEnrollmentRepository enrollmentRepository,
            CenterSessionPackRepository packRepository,
            CenterStudentPackRepository studentPackRepository,
            CenterLockRepository centerLockRepository,
            AcademicStructurePort academicStructurePort,
            TeacherDirectoryPort teacherDirectoryPort,
            StudentDirectoryPort studentDirectoryPort,
            CenterSubscriptionGuard subscriptionGuard,
            CenterSchoolGuard schoolGuard,
            CenterTutorScopeGuard tutorScopeGuard,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.groupRepository = groupRepository;
        this.groupTutorRepository = groupTutorRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.packRepository = packRepository;
        this.studentPackRepository = studentPackRepository;
        this.centerLockRepository = centerLockRepository;
        this.academicStructurePort = academicStructurePort;
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.studentDirectoryPort = studentDirectoryPort;
        this.subscriptionGuard = subscriptionGuard;
        this.schoolGuard = schoolGuard;
        this.tutorScopeGuard = tutorScopeGuard;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @PreAuthorize(CenterRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<GroupView> listGroups(
            UUID academicYearId,
            UUID gradeLevelId,
            UUID subjectId,
            CenterGroupType groupType,
            CenterGroupStatus status,
            Pageable pageable) {
        subscriptionGuard.requireReadAccess();
        Set<UUID> restrictedGroupIds = tutorScopeGuard.isRestrictedTutor()
                ? tutorScopeGuard.currentTutorGroupIds()
                : null;
        if (restrictedGroupIds != null && restrictedGroupIds.isEmpty()) {
            return Page.empty(pageable);
        }
        Specification<CenterGroup> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (restrictedGroupIds != null) {
                predicates.add(root.get("id").in(restrictedGroupIds));
            }
            if (academicYearId != null) {
                predicates.add(cb.equal(root.get("academicYearId"), academicYearId));
            }
            if (gradeLevelId != null) {
                predicates.add(cb.equal(root.get("gradeLevelId"), gradeLevelId));
            }
            if (subjectId != null) {
                predicates.add(cb.equal(root.get("subjectId"), subjectId));
            }
            if (groupType != null) {
                predicates.add(cb.equal(root.get("groupType"), groupType));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<CenterGroup> page = groupRepository.findAll(spec, pageable);
        if (page.isEmpty()) {
            return page.map(g -> new GroupView(g, 0L));
        }
        Set<UUID> groupIds = page.getContent().stream().map(CenterGroup::getId).collect(Collectors.toSet());
        Map<UUID, Long> activeCountsByGroup = enrollmentRepository
                .countByGroupIdInAndStatus(groupIds, CenterGroupEnrollmentStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(
                        CenterGroupEnrollmentRepository.GroupEnrollmentCount::getGroupId,
                        CenterGroupEnrollmentRepository.GroupEnrollmentCount::getEnrollmentCount));
        return page.map(g -> new GroupView(g, activeCountsByGroup.getOrDefault(g.getId(), 0L)));
    }

    @PreAuthorize(CenterRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public GroupView getGroup(UUID groupId) {
        subscriptionGuard.requireReadAccess();
        CenterGroup group = requireGroup(groupId);
        tutorScopeGuard.assertCanAccessGroup(group.getId());
        return toGroupView(group);
    }

    @PreAuthorize(CenterRoles.GROUP_MANAGE_ROLES)
    @Transactional
    public GroupView createGroup(
            UUID academicYearId,
            UUID gradeLevelId,
            UUID subjectId,
            UUID defaultPackId,
            String code,
            String name,
            String description,
            CenterGroupType groupType,
            String roomName,
            int capacity,
            LocalDate startDate,
            LocalDate endDate) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        validateMutableAcademicYear(academicYearId);
        validateOptionalGradeLevelAndSubject(gradeLevelId, subjectId);
        validateOptionalDefaultPack(defaultPackId);

        String normalizedCode = CenterGroup.normalizeCode(code);
        if (groupRepository.existsByAcademicYearIdAndCode(academicYearId, normalizedCode)) {
            throw new CenterConflictException(
                    "center-group-already-exists",
                    "Code cohorte déjà utilisé",
                    "Une cohorte avec le code " + normalizedCode + " existe déjà pour cette année scolaire.");
        }
        CenterGroup group = new CenterGroup(
                academicYearId,
                gradeLevelId,
                subjectId,
                defaultPackId,
                normalizedCode,
                name,
                description,
                groupType,
                roomName,
                capacity,
                startDate,
                endDate);
        CenterGroup saved = groupRepository.save(group);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.CENTER,
                AuditAction.CENTER_GROUP_CREATED,
                "CENTER_GROUP",
                saved.getId(),
                "Création d'une cohorte de renforcement.",
                Map.of(
                        "code", saved.getCode(),
                        "groupType", saved.getGroupType().name(),
                        "capacity", saved.getCapacity())));
        return toGroupView(saved);
    }

    @PreAuthorize(CenterRoles.GROUP_MANAGE_ROLES)
    @Transactional
    public GroupView updateGroup(
            UUID groupId,
            UUID gradeLevelId,
            UUID subjectId,
            UUID defaultPackId,
            String name,
            String description,
            CenterGroupType groupType,
            String roomName,
            int capacity,
            LocalDate startDate,
            LocalDate endDate) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterGroup group = requireGroup(groupId);
        validateMutableAcademicYear(group.getAcademicYearId());
        validateOptionalGradeLevelAndSubject(gradeLevelId, subjectId);
        validateOptionalDefaultPack(defaultPackId);
        long activeEnrollments =
                enrollmentRepository.countByGroupIdAndStatus(group.getId(), CenterGroupEnrollmentStatus.ACTIVE);
        group.updateDetails(
                gradeLevelId,
                subjectId,
                defaultPackId,
                name,
                description,
                groupType,
                roomName,
                capacity,
                startDate,
                endDate,
                activeEnrollments);
        return toGroupView(groupRepository.save(group));
    }

    @PreAuthorize(CenterRoles.GROUP_MANAGE_ROLES)
    @Transactional
    public GroupView activateGroup(UUID groupId) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterGroup group = requireGroup(groupId);
        validateMutableAcademicYear(group.getAcademicYearId());
        group.activate();
        CenterGroup saved = groupRepository.save(group);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.CENTER,
                AuditAction.CENTER_GROUP_STATUS_CHANGED,
                "CENTER_GROUP",
                saved.getId(),
                "Activation d'une cohorte de renforcement.",
                Map.of("code", saved.getCode(), "status", saved.getStatus().name())));
        return toGroupView(saved);
    }

    @PreAuthorize(CenterRoles.GROUP_MANAGE_ROLES)
    @Transactional
    public GroupView completeGroup(UUID groupId, String reason) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterGroup group = requireGroup(groupId);
        group.complete(reason);
        CenterGroup saved = groupRepository.save(group);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.CENTER,
                AuditAction.CENTER_GROUP_STATUS_CHANGED,
                "CENTER_GROUP",
                saved.getId(),
                "Clôture d'une cohorte de renforcement.",
                Map.of("code", saved.getCode(), "status", saved.getStatus().name())));
        return toGroupView(saved);
    }

    @PreAuthorize(CenterRoles.GROUP_MANAGE_ROLES)
    @Transactional
    public GroupView archiveGroup(UUID groupId, String reason) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterGroup group = requireGroup(groupId);
        group.archive(reason, clock.instant());
        CenterGroup saved = groupRepository.save(group);
        auditLogPort.recordTenantEvent(AuditRecordCommand.warning(
                AuditCategory.CENTER,
                AuditAction.CENTER_GROUP_STATUS_CHANGED,
                AuditOutcome.SUCCESS,
                "CENTER_GROUP",
                saved.getId(),
                "Archivage d'une cohorte de renforcement.",
                Map.of("code", saved.getCode(), "status", saved.getStatus().name())));
        return toGroupView(saved);
    }

    // --- Affectations d'intervenants ---

    @PreAuthorize(CenterRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public List<GroupTutorView> listGroupTutors(UUID groupId) {
        subscriptionGuard.requireReadAccess();
        CenterGroup group = requireGroup(groupId);
        tutorScopeGuard.assertCanAccessGroup(group.getId());
        List<CenterGroupTutor> assignments =
                groupTutorRepository.findByGroupIdOrderByActiveDescLeadTutorDescCreatedAtAsc(group.getId());
        if (assignments.isEmpty()) {
            return List.of();
        }
        Set<UUID> teacherIds = assignments.stream()
                .map(CenterGroupTutor::getTeacherId)
                .collect(Collectors.toSet());
        Map<UUID, TeacherSummary> teachersById = teacherDirectoryPort.findTeachersByIds(teacherIds);
        return assignments.stream()
                .map(a -> new GroupTutorView(a, teachersById.get(a.getTeacherId())))
                .toList();
    }

    @PreAuthorize(CenterRoles.GROUP_MANAGE_ROLES)
    @Transactional
    public GroupTutorView assignTutor(
            UUID groupId,
            UUID teacherId,
            UUID subjectId,
            boolean leadTutor,
            Long hourlyRateXof,
            LocalDate startDate,
            LocalDate endDate,
            String notes) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterGroup group = requireGroup(groupId);
        if (!group.isOperationalForEnrollmentsAndPlanning()) {
            throw new CenterConflictException(
                    "center-group-immutable",
                    "Cohorte non opérationnelle",
                    "Impossible d'affecter un intervenant sur une cohorte clôturée ou archivée.");
        }
        TeacherSummary teacher = teacherDirectoryPort.findTeacherById(teacherId)
                .orElseThrow(() -> new CenterNotFoundException(
                        "center-teacher-not-found", "Intervenant introuvable", "L'intervenant demandé est introuvable."));
        if (!teacher.active()) {
            throw new CenterConflictException(
                    "center-teacher-inactive",
                    "Intervenant inactif",
                    "Seul un enseignant/intervenant au statut ACTIVE peut être affecté à une cohorte.");
        }
        if (subjectId != null) {
            SubjectSummary subject = academicStructurePort.findSubjectById(subjectId)
                    .orElseThrow(() -> new CenterNotFoundException(
                            "center-subject-not-found", "Matière introuvable", "La matière référencée est introuvable."));
            if (!subject.active()) {
                throw new CenterConflictException(
                        "center-subject-inactive", "Matière inactive", "La matière référencée est inactive.");
            }
        }
        boolean duplicate = subjectId == null
                ? groupTutorRepository.existsByGroupIdAndTeacherIdAndSubjectIdIsNullAndActiveTrue(groupId, teacherId)
                : groupTutorRepository.existsByGroupIdAndTeacherIdAndSubjectIdAndActiveTrue(
                        groupId, teacherId, subjectId);
        if (duplicate) {
            throw new CenterConflictException(
                    "center-tutor-already-assigned",
                    "Intervenant déjà affecté",
                    "Cet intervenant dispose déjà d'une affectation active pour cette cohorte et cette matière.");
        }
        if (leadTutor) {
            groupTutorRepository.findByGroupIdAndLeadTutorTrueAndActiveTrue(groupId).ifPresent(previousLead -> {
                previousLead.unsetLeadTutor();
                groupTutorRepository.saveAndFlush(previousLead);
            });
        }
        CenterGroupTutor assignment = new CenterGroupTutor(
                groupId, teacherId, subjectId, leadTutor, hourlyRateXof, startDate, endDate, notes);
        CenterGroupTutor saved = groupTutorRepository.save(assignment);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.CENTER,
                AuditAction.CENTER_GROUP_TUTOR_ASSIGNED,
                "CENTER_GROUP_TUTOR",
                saved.getId(),
                "Affectation d'un intervenant à une cohorte de renforcement.",
                Map.of(
                        "groupId", groupId.toString(),
                        "teacherId", teacherId.toString(),
                        "leadTutor", leadTutor)));
        return new GroupTutorView(saved, teacher);
    }

    @PreAuthorize(CenterRoles.GROUP_MANAGE_ROLES)
    @Transactional
    public GroupTutorView deactivateTutorAssignment(UUID groupId, UUID assignmentId, LocalDate endDate, String reason) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        requireGroup(groupId);
        CenterGroupTutor assignment = groupTutorRepository.findByIdAndGroupId(assignmentId, groupId)
                .orElseThrow(() -> new CenterNotFoundException(
                        "center-tutor-assignment-not-found",
                        "Affectation introuvable",
                        "L'affectation d'intervenant demandée est introuvable."));
        assignment.deactivate(endDate, reason);
        return toGroupTutorView(groupTutorRepository.save(assignment));
    }

    // --- Inscriptions des élèves ---

    @PreAuthorize(CenterRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public List<GroupEnrollmentView> listGroupEnrollments(UUID groupId) {
        subscriptionGuard.requireReadAccess();
        CenterGroup group = requireGroup(groupId);
        tutorScopeGuard.assertCanAccessGroup(group.getId());
        List<CenterGroupEnrollment> enrollments =
                enrollmentRepository.findByGroupIdOrderByEnrolledAtAscCreatedAtAsc(group.getId());
        Set<UUID> studentIds = enrollments.stream()
                .map(CenterGroupEnrollment::getStudentId)
                .collect(Collectors.toSet());
        Map<UUID, StudentSummary> studentsById = studentIds.isEmpty()
                ? Map.of()
                : studentDirectoryPort.findStudentsByIds(studentIds).stream()
                        .collect(Collectors.toMap(StudentSummary::id, Function.identity()));
        return enrollments.stream()
                .map(e -> new GroupEnrollmentView(e, studentsById.get(e.getStudentId())))
                .toList();
    }

    @PreAuthorize(CenterRoles.ENROLLMENT_WRITE_ROLES)
    @Transactional
    public GroupEnrollmentView enrollStudent(
            UUID groupId,
            UUID studentId,
            UUID studentPackId,
            LocalDate enrolledAt,
            String notes) {
        subscriptionGuard.requireWriteAccess();
        SchoolProfileSnapshot school = schoolGuard.requireActiveSchoolForWrite();
        CenterGroup group = requireGroup(groupId);
        if (!group.isOperationalForEnrollmentsAndPlanning()) {
            throw new CenterConflictException(
                    "center-group-immutable",
                    "Cohorte fermée aux inscriptions",
                    "Impossible d'inscrire un élève dans une cohorte clôturée ou archivée.");
        }
        StudentSummary student = studentDirectoryPort.findStudentById(studentId)
                .orElseThrow(() -> new CenterNotFoundException(
                        "center-student-not-found", "Élève introuvable", "L'élève demandé est introuvable."));
        if (!student.active()) {
            throw new CenterConflictException(
                    "center-student-inactive",
                    "Élève inactif",
                    "Seul un élève au statut ACTIVE peut être inscrit à une cohorte.");
        }
        centerLockRepository.lockGroupCapacity(groupId);
        if (enrollmentRepository.existsByGroupIdAndStudentIdAndStatus(
                groupId, studentId, CenterGroupEnrollmentStatus.ACTIVE)) {
            throw new CenterConflictException(
                    "center-enrollment-already-exists",
                    "Élève déjà inscrit",
                    "Cet élève possède déjà une inscription active dans cette cohorte.");
        }
        long activeCount =
                enrollmentRepository.countByGroupIdAndStatus(groupId, CenterGroupEnrollmentStatus.ACTIVE);
        if (activeCount >= group.getCapacity()) {
            throw new CenterConflictException(
                    "center-group-capacity-exceeded",
                    "Capacité de la cohorte atteinte",
                    "La cohorte " + group.getCode() + " a atteint sa capacité maximale (" + group.getCapacity() + ").");
        }
        if (studentPackId != null) {
            CenterStudentPack studentPack = studentPackRepository.findById(studentPackId)
                    .orElseThrow(() -> new CenterNotFoundException(
                            "center-student-pack-not-found",
                            "Forfait élève introuvable",
                            "Le forfait élève référencé est introuvable."));
            if (!studentId.equals(studentPack.getStudentId())) {
                throw new CenterValidationException(
                        "center-enrollment-invalid",
                        "Forfait incohérent",
                        "Le forfait élève référencé appartient à un autre élève.");
            }
            if (studentPack.getGroupId() != null && !groupId.equals(studentPack.getGroupId())) {
                throw new CenterValidationException(
                        "center-enrollment-invalid",
                        "Forfait incohérent",
                        "Le forfait élève référencé est rattaché à une autre cohorte.");
            }
            if (studentPack.getStatus() != CenterStudentPackStatus.ACTIVE) {
                throw new CenterConflictException(
                        "center-pack-inactive",
                        "Forfait non actif",
                        "Seul un forfait élève ACTIVE peut être rattaché lors d'une inscription.");
            }
        }
        LocalDate effectiveDate = enrolledAt != null ? enrolledAt : schoolGuard.todayInSchoolZone(clock, school);
        CenterGroupEnrollment enrollment = new CenterGroupEnrollment(
                groupId, studentId, studentPackId, effectiveDate, notes);
        CenterGroupEnrollment saved = enrollmentRepository.save(enrollment);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.CENTER,
                AuditAction.CENTER_GROUP_STUDENT_ENROLLED,
                "CENTER_GROUP_ENROLLMENT",
                saved.getId(),
                "Inscription d'un élève dans une cohorte de renforcement.",
                Map.of(
                        "groupId", groupId.toString(),
                        "studentId", studentId.toString())));
        return new GroupEnrollmentView(saved, student);
    }

    @PreAuthorize(CenterRoles.ENROLLMENT_WRITE_ROLES)
    @Transactional
    public GroupEnrollmentView transitionEnrollmentStatus(
            UUID groupId,
            UUID enrollmentId,
            CenterGroupEnrollmentStatus targetStatus,
            LocalDate endedAt,
            String reason) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        requireGroup(groupId);
        CenterGroupEnrollment enrollment = enrollmentRepository.findByIdAndGroupId(enrollmentId, groupId)
                .orElseThrow(() -> new CenterNotFoundException(
                        "center-enrollment-not-found",
                        "Inscription introuvable",
                        "L'inscription demandée dans cette cohorte est introuvable."));
        enrollment.transitionStatus(targetStatus, endedAt, reason);
        CenterGroupEnrollment saved = enrollmentRepository.save(enrollment);
        StudentSummary student = studentDirectoryPort.findStudentById(saved.getStudentId()).orElse(null);
        return new GroupEnrollmentView(saved, student);
    }

    private void validateMutableAcademicYear(UUID academicYearId) {
        AcademicYearSummary year = academicStructurePort.findAcademicYearById(academicYearId)
                .orElseThrow(() -> new CenterNotFoundException(
                        "center-academic-year-not-found",
                        "Année scolaire introuvable",
                        "L'année scolaire référencée est introuvable."));
        if ("CLOSED".equals(year.status()) || "ARCHIVED".equals(year.status())) {
            throw new CenterConflictException(
                    "center-academic-year-closed",
                    "Année scolaire clôturée",
                    "Impossible de modifier une cohorte sur une année scolaire clôturée ou archivée.");
        }
    }

    private void validateOptionalGradeLevelAndSubject(UUID gradeLevelId, UUID subjectId) {
        if (gradeLevelId != null) {
            GradeLevelSummary level = academicStructurePort.findGradeLevelById(gradeLevelId)
                    .orElseThrow(() -> new CenterNotFoundException(
                            "center-grade-level-not-found", "Niveau introuvable", "Le niveau référencé est introuvable."));
            if (!level.active()) {
                throw new CenterConflictException(
                        "center-grade-level-inactive", "Niveau inactif", "Le niveau référencé est inactif.");
            }
        }
        if (subjectId != null) {
            SubjectSummary subject = academicStructurePort.findSubjectById(subjectId)
                    .orElseThrow(() -> new CenterNotFoundException(
                            "center-subject-not-found", "Matière introuvable", "La matière référencée est introuvable."));
            if (!subject.active()) {
                throw new CenterConflictException(
                        "center-subject-inactive", "Matière inactive", "La matière référencée est inactive.");
            }
        }
    }

    private void validateOptionalDefaultPack(UUID defaultPackId) {
        if (defaultPackId != null) {
            CenterSessionPack pack = packRepository.findById(defaultPackId)
                    .orElseThrow(() -> new CenterNotFoundException(
                            "center-pack-not-found", "Forfait introuvable", "Le forfait par défaut référencé est introuvable."));
            if (!pack.isActive()) {
                throw new CenterConflictException(
                        "center-pack-inactive", "Forfait inactif", "Le forfait par défaut référencé est désactivé.");
            }
        }
    }

    private CenterGroup requireGroup(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new CenterNotFoundException(
                        "center-group-not-found", "Cohorte introuvable", "La cohorte demandée est introuvable."));
    }

    private GroupView toGroupView(CenterGroup group) {
        long activeCount =
                enrollmentRepository.countByGroupIdAndStatus(group.getId(), CenterGroupEnrollmentStatus.ACTIVE);
        return new GroupView(group, activeCount);
    }

    private GroupTutorView toGroupTutorView(CenterGroupTutor assignment) {
        TeacherSummary teacher = teacherDirectoryPort.findTeacherById(assignment.getTeacherId()).orElse(null);
        return new GroupTutorView(assignment, teacher);
    }

    public record GroupView(CenterGroup group, long activeEnrollmentCount) {
    }

    public record GroupTutorView(CenterGroupTutor assignment, TeacherSummary teacher) {
    }

    public record GroupEnrollmentView(CenterGroupEnrollment enrollment, StudentSummary student) {
    }
}
