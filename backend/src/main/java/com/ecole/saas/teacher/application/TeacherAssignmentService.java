package com.ecole.saas.teacher.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.AcademicYearSummary;
import com.ecole.saas.academic.ClassRoomSummary;
import com.ecole.saas.academic.ClassSubjectSummary;
import com.ecole.saas.academic.SubjectSummary;
import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.teacher.domain.Teacher;
import com.ecole.saas.teacher.domain.TeacherAssignment;
import com.ecole.saas.teacher.domain.TeacherConflictException;
import com.ecole.saas.teacher.domain.TeacherNotFoundException;
import com.ecole.saas.teacher.domain.TeacherValidationException;
import com.ecole.saas.teacher.infrastructure.TeacherAssignmentRepository;
import com.ecole.saas.teacher.infrastructure.TeacherRepository;
import jakarta.persistence.criteria.Predicate;
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
 * Service applicatif de gestion des affectations des enseignants aux classes et matières
 * par année scolaire (validées contre {@link AcademicStructurePort}).
 */
@Service
public class TeacherAssignmentService {

    public record AssignmentView(
            TeacherAssignment assignment,
            String teacherCode,
            String teacherFullName,
            String academicYearCode,
            String classRoomCode,
            String classRoomName,
            String subjectCode,
            String subjectName) {
    }

    private final TeacherAssignmentRepository assignmentRepository;
    private final TeacherRepository teacherRepository;
    private final AcademicStructurePort academicStructurePort;
    private final TeacherSubscriptionGuard subscriptionGuard;
    private final AuditLogPort auditLogPort;

    public TeacherAssignmentService(
            TeacherAssignmentRepository assignmentRepository,
            TeacherRepository teacherRepository,
            AcademicStructurePort academicStructurePort,
            TeacherSubscriptionGuard subscriptionGuard,
            AuditLogPort auditLogPort) {
        this.assignmentRepository = assignmentRepository;
        this.teacherRepository = teacherRepository;
        this.academicStructurePort = academicStructurePort;
        this.subscriptionGuard = subscriptionGuard;
        this.auditLogPort = auditLogPort;
    }

    @PreAuthorize(TeacherRoles.BACKOFFICE_READ_ROLES)
    @Transactional(readOnly = true)
    public Page<AssignmentView> listAssignments(
            UUID teacherId,
            UUID academicYearId,
            UUID classRoomId,
            UUID subjectId,
            Boolean active,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Specification<TeacherAssignment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (teacherId != null) {
                predicates.add(cb.equal(root.get("teacherId"), teacherId));
            }
            if (academicYearId != null) {
                predicates.add(cb.equal(root.get("academicYearId"), academicYearId));
            }
            if (classRoomId != null) {
                predicates.add(cb.equal(root.get("classRoomId"), classRoomId));
            }
            if (subjectId != null) {
                predicates.add(cb.equal(root.get("subjectId"), subjectId));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return enrichPage(assignmentRepository.findAll(spec, pageable));
    }

    @PreAuthorize(TeacherRoles.SELF_ROLES)
    @Transactional(readOnly = true)
    public List<AssignmentView> listOwnAssignments(UUID currentUserId, UUID academicYearId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Teacher teacher = teacherRepository.findByUserId(currentUserId).orElse(null);
        if (teacher == null || !teacher.isActive()) {
            return List.of();
        }
        List<TeacherAssignment> assignments = assignmentRepository
                .findByTeacherIdAndActiveTrueOrderByCreatedAtAsc(teacher.getId())
                .stream()
                .filter(a -> academicYearId == null || academicYearId.equals(a.getAcademicYearId()))
                .toList();
        return assignments.stream().map(a -> toView(a, teacher)).toList();
    }

    @PreAuthorize(TeacherRoles.BACKOFFICE_READ_ROLES)
    @Transactional(readOnly = true)
    public AssignmentView getAssignment(UUID assignmentId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        TeacherAssignment assignment = requireAssignment(assignmentId);
        Teacher teacher = teacherRepository.findById(assignment.getTeacherId()).orElse(null);
        return toView(assignment, teacher);
    }

    @PreAuthorize(TeacherRoles.WRITE_ROLES)
    @Transactional
    public AssignmentView createAssignment(
            UUID teacherId,
            UUID academicYearId,
            UUID classRoomId,
            UUID subjectId,
            boolean homeroomTeacher,
            Integer weeklyHours,
            LocalDate startDate,
            LocalDate endDate,
            String notes) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new TeacherNotFoundException("Dossier enseignant introuvable."));
        teacher.assertActive();

        AcademicYearSummary year = academicStructurePort.findAcademicYearById(academicYearId)
                .orElseThrow(() -> new TeacherNotFoundException(
                        "academic-year-not-found", "Année scolaire introuvable", "Année scolaire introuvable."));
        assertAcademicYearMutable(year);

        ClassRoomSummary classRoom = academicStructurePort.findClassRoomById(classRoomId)
                .orElseThrow(() -> new TeacherNotFoundException(
                        "classroom-not-found", "Classe introuvable", "Classe introuvable."));
        if (!"ACTIVE".equals(classRoom.status())) {
            throw new TeacherConflictException(
                    "classroom-archived",
                    "Classe archivée",
                    "Impossible d'affecter un enseignant sur une classe archivée.");
        }
        if (!academicYearId.equals(classRoom.academicYearId())) {
            throw new TeacherValidationException(
                    "La classe sélectionnée n'appartient pas à l'année scolaire indiquée.");
        }

        ClassSubjectSummary classSubject = academicStructurePort.findActiveSubjectsForClass(classRoomId).stream()
                .filter(cs -> subjectId.equals(cs.subjectId()))
                .findFirst()
                .orElseThrow(() -> new TeacherConflictException(
                        "class-subject-not-active",
                        "Matière non affectée à la classe",
                        "La matière demandée n'est pas activement affectée à cette classe."));

        if (assignmentRepository.existsByTeacherIdAndClassRoomIdAndSubjectIdAndActiveTrue(
                teacherId, classRoomId, subjectId)) {
            throw new TeacherConflictException(
                    "teacher-assignment-already-exists",
                    "Affectation déjà existante",
                    "Cet enseignant est déjà activement affecté à cette classe pour cette matière.");
        }

        if (homeroomTeacher) {
            clearExistingHomeroom(classRoomId, null);
        }

        TeacherAssignment saved = assignmentRepository.save(new TeacherAssignment(
                teacherId,
                academicYearId,
                classRoomId,
                subjectId,
                classSubject.id(),
                homeroomTeacher,
                weeklyHours,
                startDate,
                endDate,
                notes));
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.TEACHER,
                AuditAction.TEACHER_ASSIGNMENT_CREATED,
                "TEACHER_ASSIGNMENT",
                saved.getId(),
                "Affectation d'un enseignant à une classe et une matière.",
                Map.of(
                        "teacherId", teacherId.toString(),
                        "classRoomId", classRoomId.toString(),
                        "subjectId", subjectId.toString(),
                        "homeroomTeacher", homeroomTeacher)));
        return new AssignmentView(
                saved,
                teacher.getEmployeeCode(),
                teacher.getFullName(),
                year.code(),
                classRoom.code(),
                classRoom.name(),
                classSubject.subjectCode(),
                classSubject.subjectName());
    }

    @PreAuthorize(TeacherRoles.WRITE_ROLES)
    @Transactional
    public AssignmentView updateAssignment(
            UUID assignmentId,
            boolean homeroomTeacher,
            Integer weeklyHours,
            LocalDate startDate,
            LocalDate endDate,
            String notes) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        TeacherAssignment assignment = requireAssignment(assignmentId);
        assignment.assertActive();

        AcademicYearSummary year = academicStructurePort.findAcademicYearById(assignment.getAcademicYearId())
                .orElseThrow(() -> new TeacherNotFoundException(
                        "academic-year-not-found", "Année scolaire introuvable", "Année scolaire introuvable."));
        assertAcademicYearMutable(year);

        if (homeroomTeacher && !assignment.isHomeroomTeacher()) {
            clearExistingHomeroom(assignment.getClassRoomId(), assignment.getId());
        }

        assignment.updateDetails(homeroomTeacher, weeklyHours, startDate, endDate, notes);
        TeacherAssignment saved = assignmentRepository.save(assignment);
        Teacher teacher = teacherRepository.findById(saved.getTeacherId()).orElse(null);
        return toView(saved, teacher);
    }

    @PreAuthorize(TeacherRoles.WRITE_ROLES)
    @Transactional
    public AssignmentView deactivateAssignment(UUID assignmentId, LocalDate endDate, String reason) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        TeacherAssignment assignment = requireAssignment(assignmentId);
        AcademicYearSummary year = academicStructurePort.findAcademicYearById(assignment.getAcademicYearId())
                .orElseThrow(() -> new TeacherNotFoundException(
                        "academic-year-not-found", "Année scolaire introuvable", "Année scolaire introuvable."));
        assertAcademicYearMutable(year);

        assignment.deactivate(endDate, reason);
        TeacherAssignment saved = assignmentRepository.save(assignment);
        auditLogPort.recordTenantEvent(AuditRecordCommand.warning(
                AuditCategory.TEACHER,
                AuditAction.TEACHER_ASSIGNMENT_DEACTIVATED,
                AuditOutcome.SUCCESS,
                "TEACHER_ASSIGNMENT",
                saved.getId(),
                "Désactivation d'une affectation enseignant.",
                Map.of(
                        "teacherId", saved.getTeacherId().toString(),
                        "classRoomId", saved.getClassRoomId().toString(),
                        "subjectId", saved.getSubjectId().toString())));
        Teacher teacher = teacherRepository.findById(saved.getTeacherId()).orElse(null);
        return toView(saved, teacher);
    }

    private void clearExistingHomeroom(UUID classRoomId, UUID excludedAssignmentId) {
        assignmentRepository.findByClassRoomIdAndHomeroomTeacherTrueAndActiveTrue(classRoomId)
                .filter(existing -> !existing.getId().equals(excludedAssignmentId))
                .ifPresent(existing -> {
                    existing.clearHomeroom();
                    assignmentRepository.saveAndFlush(existing);
                });
    }

    private static void assertAcademicYearMutable(AcademicYearSummary year) {
        if ("CLOSED".equals(year.status()) || "ARCHIVED".equals(year.status())) {
            throw new TeacherConflictException(
                    "academic-year-immutable",
                    "Année scolaire figée",
                    "Impossible de modifier une affectation enseignant sur une année scolaire clôturée ou archivée.");
        }
    }

    private TeacherAssignment requireAssignment(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new TeacherNotFoundException(
                        "teacher-assignment-not-found",
                        "Affectation enseignant introuvable",
                        "L'affectation enseignant demandée est introuvable."));
    }

    private Page<AssignmentView> enrichPage(Page<TeacherAssignment> page) {
        Set<UUID> teacherIds = page.getContent().stream()
                .map(TeacherAssignment::getTeacherId)
                .collect(Collectors.toSet());
        Map<UUID, Teacher> teachersById = teacherRepository.findAllById(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity()));
        return page.map(a -> toView(a, teachersById.get(a.getTeacherId())));
    }

    private AssignmentView toView(TeacherAssignment assignment, Teacher teacher) {
        AcademicYearSummary year = academicStructurePort.findAcademicYearById(assignment.getAcademicYearId()).orElse(null);
        ClassRoomSummary room = academicStructurePort.findClassRoomById(assignment.getClassRoomId()).orElse(null);
        SubjectSummary subject = academicStructurePort.findSubjectById(assignment.getSubjectId()).orElse(null);
        return new AssignmentView(
                assignment,
                teacher != null ? teacher.getEmployeeCode() : null,
                teacher != null ? teacher.getFullName() : null,
                year != null ? year.code() : null,
                room != null ? room.code() : null,
                room != null ? room.name() : null,
                subject != null ? subject.code() : null,
                subject != null ? subject.name() : null);
    }
}
