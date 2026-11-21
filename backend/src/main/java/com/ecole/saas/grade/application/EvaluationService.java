package com.ecole.saas.grade.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.AcademicYearSummary;
import com.ecole.saas.academic.ClassRoomSummary;
import com.ecole.saas.academic.ClassSubjectSummary;
import com.ecole.saas.academic.EvaluationPeriodSummary;
import com.ecole.saas.academic.SubjectSummary;
import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.grade.domain.Evaluation;
import com.ecole.saas.grade.domain.EvaluationPeriodClosedException;
import com.ecole.saas.grade.domain.EvaluationStatus;
import com.ecole.saas.grade.domain.EvaluationType;
import com.ecole.saas.grade.domain.GradeAttendanceStatus;
import com.ecole.saas.grade.domain.GradeConflictException;
import com.ecole.saas.grade.domain.GradeForbiddenException;
import com.ecole.saas.grade.domain.GradeNotFoundException;
import com.ecole.saas.grade.domain.GradeValidationException;
import com.ecole.saas.grade.domain.StudentGrade;
import com.ecole.saas.grade.infrastructure.EvaluationRepository;
import com.ecole.saas.grade.infrastructure.StudentGradeRepository;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentEnrollmentSummary;
import com.ecole.saas.student.StudentSummary;
import com.ecole.saas.teacher.TeacherAssignmentSummary;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import com.ecole.saas.teacher.TeacherSummary;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * Service applicatif de gestion des évaluations et de la saisie/validation des notes.
 * Applique le blocage strict dès que la période d'évaluation n'est pas {@code OPEN}
 * (notamment {@code CLOSED} ou {@code LOCKED}) ainsi que la restriction fine des enseignants.
 */
@Service
public class EvaluationService {

    public record EvaluationView(
            Evaluation evaluation,
            String periodCode,
            String periodStatus,
            String classRoomCode,
            String classRoomName,
            String subjectCode,
            String subjectName,
            String teacherFullName) {
    }

    public record StudentGradeInput(
            UUID studentId,
            GradeAttendanceStatus attendanceStatus,
            BigDecimal score,
            String comment) {
    }

    public record StudentGradeView(
            StudentGrade grade,
            String studentMatricule,
            String studentFirstName,
            String studentLastName) {
    }

    private final EvaluationRepository evaluationRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final AcademicStructurePort academicStructurePort;
    private final StudentDirectoryPort studentDirectoryPort;
    private final TeacherDirectoryPort teacherDirectoryPort;
    private final GradeSchoolContext schoolContext;
    private final GradeSubscriptionGuard subscriptionGuard;
    private final GradeTeacherScopeGuard teacherScopeGuard;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public EvaluationService(
            EvaluationRepository evaluationRepository,
            StudentGradeRepository studentGradeRepository,
            AcademicStructurePort academicStructurePort,
            StudentDirectoryPort studentDirectoryPort,
            TeacherDirectoryPort teacherDirectoryPort,
            GradeSchoolContext schoolContext,
            GradeSubscriptionGuard subscriptionGuard,
            GradeTeacherScopeGuard teacherScopeGuard,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.evaluationRepository = evaluationRepository;
        this.studentGradeRepository = studentGradeRepository;
        this.academicStructurePort = academicStructurePort;
        this.studentDirectoryPort = studentDirectoryPort;
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.schoolContext = schoolContext;
        this.subscriptionGuard = subscriptionGuard;
        this.teacherScopeGuard = teacherScopeGuard;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @PreAuthorize(GradeRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<EvaluationView> listEvaluations(
            UUID academicYearId,
            UUID evaluationPeriodId,
            UUID classRoomId,
            UUID subjectId,
            EvaluationStatus status,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        List<TeacherAssignmentSummary> teacherAssignments = null;
        if (teacherScopeGuard.isRestrictedTeacher()) {
            if (classRoomId != null && subjectId != null) {
                teacherScopeGuard.assertCanAccessClassSubject(classRoomId, subjectId);
            } else if (classRoomId != null) {
                teacherScopeGuard.assertCanAccessClass(classRoomId);
            }
            teacherAssignments = teacherScopeGuard.currentTeacherAssignments();
            if (teacherAssignments.isEmpty()) {
                return Page.empty(pageable);
            }
        }
        List<TeacherAssignmentSummary> scopedAssignments = teacherAssignments;

        Specification<Evaluation> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (scopedAssignments != null) {
                List<Predicate> pairPredicates = new ArrayList<>();
                for (TeacherAssignmentSummary assignment : scopedAssignments) {
                    pairPredicates.add(cb.and(
                            cb.equal(root.get("classRoomId"), assignment.classRoomId()),
                            cb.equal(root.get("subjectId"), assignment.subjectId())));
                }
                predicates.add(cb.or(pairPredicates.toArray(Predicate[]::new)));
            }
            if (academicYearId != null) {
                predicates.add(cb.equal(root.get("academicYearId"), academicYearId));
            }
            if (evaluationPeriodId != null) {
                predicates.add(cb.equal(root.get("evaluationPeriodId"), evaluationPeriodId));
            }
            if (classRoomId != null) {
                predicates.add(cb.equal(root.get("classRoomId"), classRoomId));
            }
            if (subjectId != null) {
                predicates.add(cb.equal(root.get("subjectId"), subjectId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return enrichEvaluationPage(evaluationRepository.findAll(spec, pageable));
    }

    @PreAuthorize(GradeRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public EvaluationView getEvaluation(UUID evaluationId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();
        Evaluation evaluation = requireEvaluation(evaluationId);
        teacherScopeGuard.assertCanAccessClassSubject(evaluation.getClassRoomId(), evaluation.getSubjectId());
        return toEvaluationView(evaluation);
    }

    @PreAuthorize(GradeRoles.EVALUATION_WRITE_ROLES)
    @Transactional
    public EvaluationView createEvaluation(
            UUID evaluationPeriodId,
            UUID classRoomId,
            UUID subjectId,
            UUID teacherId,
            String title,
            EvaluationType evaluationType,
            LocalDate evaluationDate,
            BigDecimal maxScore,
            Integer weight,
            String notes,
            UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        EvaluationPeriodSummary period = requirePeriod(evaluationPeriodId);
        assertPeriodOpenForGrading(period);

        AcademicYearSummary year = academicStructurePort.findAcademicYearById(period.academicYearId())
                .orElseThrow(() -> new GradeNotFoundException(
                        "academic-year-not-found", "Année scolaire introuvable", "Année scolaire introuvable."));
        assertYearMutable(year);

        ClassRoomSummary classRoom = academicStructurePort.findClassRoomById(classRoomId)
                .orElseThrow(() -> new GradeNotFoundException(
                        "classroom-not-found", "Classe introuvable", "La classe demandée est introuvable."));
        if (!"ACTIVE".equals(classRoom.status())) {
            throw new GradeConflictException(
                    "classroom-archived", "Classe archivée", "Impossible de créer une évaluation sur une classe archivée.");
        }
        if (!period.academicYearId().equals(classRoom.academicYearId())) {
            throw new GradeValidationException(
                    "La classe sélectionnée n'appartient pas à l'année scolaire de la période d'évaluation.");
        }

        ClassSubjectSummary classSubject = academicStructurePort.findActiveSubjectsForClass(classRoomId).stream()
                .filter(cs -> subjectId.equals(cs.subjectId()))
                .findFirst()
                .orElseThrow(() -> new GradeConflictException(
                        "class-subject-not-active",
                        "Matière non affectée à la classe",
                        "La matière demandée n'est pas activement affectée à cette classe."));

        teacherScopeGuard.assertCanAccessClassSubject(classRoomId, subjectId);

        validateDateInPeriod(evaluationDate, period);

        UUID resolvedTeacherId = resolveTeacherId(teacherId);
        SchoolProfileSnapshot school = schoolContext.requireSchoolProfile();
        int effectiveWeight = weight != null ? weight : 1;

        Evaluation saved = evaluationRepository.save(new Evaluation(
                period.academicYearId(),
                period.id(),
                classRoomId,
                subjectId,
                classSubject.id(),
                resolvedTeacherId,
                title,
                evaluationType,
                evaluationDate,
                maxScore,
                school.gradingScale(),
                effectiveWeight,
                actorId,
                notes));
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.GRADE,
                AuditAction.EVALUATION_CREATED,
                "EVALUATION",
                saved.getId(),
                "Création d'une évaluation scolaire.",
                Map.of(
                        "evaluationPeriodId", period.id().toString(),
                        "classRoomId", classRoomId.toString(),
                        "subjectId", subjectId.toString(),
                        "evaluationType", saved.getEvaluationType().name())));
        return toEvaluationView(saved);
    }

    @PreAuthorize(GradeRoles.EVALUATION_WRITE_ROLES)
    @Transactional
    public EvaluationView updateEvaluation(
            UUID evaluationId,
            String title,
            EvaluationType evaluationType,
            LocalDate evaluationDate,
            BigDecimal maxScore,
            Integer weight,
            String notes) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Evaluation evaluation = requireEvaluation(evaluationId);
        teacherScopeGuard.assertCanAccessClassSubject(evaluation.getClassRoomId(), evaluation.getSubjectId());

        EvaluationPeriodSummary period = requirePeriod(evaluation.getEvaluationPeriodId());
        assertPeriodOpenForGrading(period);
        assertCanMutateEvaluation(evaluation);

        validateDateInPeriod(evaluationDate, period);
        SchoolProfileSnapshot school = schoolContext.requireSchoolProfile();
        BigDecimal targetMax = maxScore != null ? maxScore : BigDecimal.valueOf(school.gradingScale());

        List<StudentGrade> existingGrades = studentGradeRepository.findByEvaluationIdOrderByCreatedAtAsc(evaluationId);
        for (StudentGrade grade : existingGrades) {
            if (grade.getScore() != null && grade.getScore().compareTo(targetMax) > 0) {
                throw new GradeConflictException(
                        "evaluation-max-score-conflict",
                        "Barème incompatible avec les notes existantes",
                        "Impossible d'abaisser le barème en dessous d'une note déjà saisie.");
            }
        }

        evaluation.updateDetails(
                title,
                evaluationType,
                evaluationDate,
                targetMax,
                school.gradingScale(),
                weight != null ? weight : evaluation.getWeight(),
                notes);
        return toEvaluationView(evaluationRepository.save(evaluation));
    }

    @PreAuthorize(GradeRoles.EVALUATION_VALIDATE_ROLES)
    @Transactional
    public EvaluationView validateEvaluation(UUID evaluationId, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Evaluation evaluation = requireEvaluation(evaluationId);
        EvaluationPeriodSummary period = requirePeriod(evaluation.getEvaluationPeriodId());
        assertPeriodOpenForGrading(period);

        evaluation.validate(actorId, clock.instant());
        Evaluation saved = evaluationRepository.save(evaluation);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.GRADE,
                AuditAction.EVALUATION_VALIDATED,
                "EVALUATION",
                saved.getId(),
                "Validation d'une évaluation scolaire.",
                Map.of(
                        "classRoomId", saved.getClassRoomId().toString(),
                        "subjectId", saved.getSubjectId().toString())));
        return toEvaluationView(saved);
    }

    @PreAuthorize(GradeRoles.EVALUATION_LOCK_ROLES)
    @Transactional
    public EvaluationView lockEvaluation(UUID evaluationId, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Evaluation evaluation = requireEvaluation(evaluationId);
        EvaluationPeriodSummary period = requirePeriod(evaluation.getEvaluationPeriodId());
        if ("LOCKED".equals(period.status())) {
            throw new EvaluationPeriodClosedException(
                    "La période d'évaluation est verrouillée et n'accepte plus aucune opération.");
        }

        evaluation.lock(actorId, clock.instant());
        Evaluation saved = evaluationRepository.save(evaluation);
        auditLogPort.recordTenantEvent(AuditRecordCommand.critical(
                AuditCategory.GRADE,
                AuditAction.EVALUATION_LOCKED,
                AuditOutcome.SUCCESS,
                "EVALUATION",
                saved.getId(),
                "Verrouillage définitif d'une évaluation scolaire.",
                Map.of(
                        "classRoomId", saved.getClassRoomId().toString(),
                        "subjectId", saved.getSubjectId().toString())));
        return toEvaluationView(saved);
    }

    @PreAuthorize(GradeRoles.EVALUATION_WRITE_ROLES)
    @Transactional
    public EvaluationView cancelEvaluation(UUID evaluationId, String reason) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        Evaluation evaluation = requireEvaluation(evaluationId);
        teacherScopeGuard.assertCanAccessClassSubject(evaluation.getClassRoomId(), evaluation.getSubjectId());

        EvaluationPeriodSummary period = requirePeriod(evaluation.getEvaluationPeriodId());
        assertPeriodOpenForGrading(period);
        assertCanMutateEvaluation(evaluation);

        evaluation.cancel(reason);
        Evaluation saved = evaluationRepository.save(evaluation);
        auditLogPort.recordTenantEvent(AuditRecordCommand.warning(
                AuditCategory.GRADE,
                AuditAction.EVALUATION_CANCELLED,
                AuditOutcome.SUCCESS,
                "EVALUATION",
                saved.getId(),
                "Annulation d'une évaluation scolaire.",
                Map.of(
                        "classRoomId", saved.getClassRoomId().toString(),
                        "subjectId", saved.getSubjectId().toString())));
        return toEvaluationView(saved);
    }

    @PreAuthorize(GradeRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public List<StudentGradeView> listGrades(UUID evaluationId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Evaluation evaluation = requireEvaluation(evaluationId);
        teacherScopeGuard.assertCanAccessClassSubject(evaluation.getClassRoomId(), evaluation.getSubjectId());

        List<StudentGrade> grades = studentGradeRepository.findByEvaluationIdOrderByCreatedAtAsc(evaluationId);
        return enrichGradeViews(grades);
    }

    @PreAuthorize(GradeRoles.EVALUATION_WRITE_ROLES)
    @Transactional
    public List<StudentGradeView> upsertGrades(UUID evaluationId, List<StudentGradeInput> entries, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        if (entries == null || entries.isEmpty()) {
            throw new GradeValidationException("Au moins une note doit être fournie pour la saisie.");
        }

        Evaluation evaluation = requireEvaluation(evaluationId);
        teacherScopeGuard.assertCanAccessClassSubject(evaluation.getClassRoomId(), evaluation.getSubjectId());

        EvaluationPeriodSummary period = requirePeriod(evaluation.getEvaluationPeriodId());
        assertPeriodOpenForGrading(period);
        assertCanMutateEvaluation(evaluation);

        Set<UUID> enrolledStudentIds = studentDirectoryPort
                .findActiveEnrollmentsByClassRoom(evaluation.getClassRoomId())
                .stream()
                .map(StudentEnrollmentSummary::studentId)
                .collect(Collectors.toSet());

        Set<UUID> seenStudentIds = new HashSet<>();
        for (StudentGradeInput entry : entries) {
            if (entry == null || entry.studentId() == null) {
                throw new GradeValidationException("L'identifiant de l'élève est obligatoire pour chaque note.");
            }
            if (!seenStudentIds.add(entry.studentId())) {
                throw new GradeValidationException("Doublon d'élève détecté dans le lot de saisie de notes.");
            }
            if (!enrolledStudentIds.contains(entry.studentId())) {
                if (studentDirectoryPort.findStudentById(entry.studentId()).isEmpty()) {
                    throw new GradeNotFoundException(
                            "student-not-found", "Élève introuvable", "L'élève demandé est introuvable.");
                }
                throw new GradeValidationException(
                        "L'élève indiqué ne possède pas d'inscription active dans cette classe.");
            }
        }

        Map<UUID, StudentGrade> existingByStudent = studentGradeRepository
                .findByEvaluationIdOrderByCreatedAtAsc(evaluationId)
                .stream()
                .collect(Collectors.toMap(StudentGrade::getStudentId, Function.identity()));

        Instant now = clock.instant();
        List<StudentGrade> toSave = new ArrayList<>();
        for (StudentGradeInput entry : entries) {
            StudentGrade existing = existingByStudent.get(entry.studentId());
            if (existing != null) {
                existing.updateGrade(
                        entry.attendanceStatus(),
                        entry.score(),
                        evaluation.getMaxScore(),
                        entry.comment(),
                        actorId);
                toSave.add(existing);
            } else {
                toSave.add(new StudentGrade(
                        evaluationId,
                        entry.studentId(),
                        entry.attendanceStatus(),
                        entry.score(),
                        evaluation.getMaxScore(),
                        entry.comment(),
                        actorId,
                        now));
            }
        }

        studentGradeRepository.saveAll(toSave);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.GRADE,
                AuditAction.GRADES_RECORDED,
                "EVALUATION",
                evaluationId,
                "Saisie ou mise à jour d'un lot de notes d'évaluation.",
                Map.of(
                        "classRoomId", evaluation.getClassRoomId().toString(),
                        "subjectId", evaluation.getSubjectId().toString(),
                        "gradeCount", toSave.size())));
        List<StudentGrade> refreshed = studentGradeRepository.findByEvaluationIdOrderByCreatedAtAsc(evaluationId);
        return enrichGradeViews(refreshed);
    }

    private void assertCanMutateEvaluation(Evaluation evaluation) {
        evaluation.assertEditable();
        if (evaluation.getStatus() == EvaluationStatus.VALIDATED && teacherScopeGuard.isRestrictedTeacher()) {
            throw new GradeForbiddenException(
                    "Une évaluation déjà validée ne peut plus être modifiée par un enseignant.");
        }
    }

    private UUID resolveTeacherId(UUID requestedTeacherId) {
        if (requestedTeacherId != null) {
            TeacherSummary teacher = teacherDirectoryPort.findTeacherById(requestedTeacherId)
                    .orElseThrow(() -> new GradeNotFoundException(
                            "teacher-not-found", "Enseignant introuvable", "L'enseignant indiqué est introuvable."));
            if (!teacher.active()) {
                throw new GradeConflictException(
                        "teacher-not-active", "Enseignant inactif", "L'enseignant indiqué n'est pas actif.");
            }
            return teacher.id();
        }
        return teacherScopeGuard.currentTeacherProfile()
                .filter(TeacherSummary::active)
                .map(TeacherSummary::id)
                .orElse(null);
    }

    static void assertPeriodOpenForGrading(EvaluationPeriodSummary period) {
        if (!"OPEN".equals(period.status())) {
            throw new EvaluationPeriodClosedException(
                    "Toute saisie ou modification de notes est bloquée car la période d'évaluation est au statut "
                            + period.status() + " (seul le statut OPEN est autorisé).");
        }
    }

    private static void assertYearMutable(AcademicYearSummary year) {
        if ("CLOSED".equals(year.status()) || "ARCHIVED".equals(year.status())) {
            throw new GradeConflictException(
                    "academic-year-immutable",
                    "Année scolaire figée",
                    "Impossible d'enregistrer une évaluation sur une année scolaire clôturée ou archivée.");
        }
    }

    private static void validateDateInPeriod(LocalDate evaluationDate, EvaluationPeriodSummary period) {
        if (evaluationDate == null) {
            throw new GradeValidationException("La date de l'évaluation est obligatoire.");
        }
        if (evaluationDate.isBefore(period.startDate()) || evaluationDate.isAfter(period.endDate())) {
            throw new GradeValidationException(
                    "La date de l'évaluation doit être comprise dans la période ("
                            + period.startDate() + " au " + period.endDate() + ").");
        }
    }

    private Evaluation requireEvaluation(UUID evaluationId) {
        return evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new GradeNotFoundException("Évaluation introuvable."));
    }

    private EvaluationPeriodSummary requirePeriod(UUID periodId) {
        return academicStructurePort.findEvaluationPeriodById(periodId)
                .orElseThrow(() -> new GradeNotFoundException(
                        "evaluation-period-not-found",
                        "Période d'évaluation introuvable",
                        "La période d'évaluation demandée est introuvable."));
    }

    private Page<EvaluationView> enrichEvaluationPage(Page<Evaluation> page) {
        if (page.isEmpty()) {
            return page.map(e -> buildEvaluationView(e, null, null, null, null));
        }
        List<Evaluation> content = page.getContent();
        Set<UUID> periodIds = content.stream()
                .map(Evaluation::getEvaluationPeriodId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> classRoomIds = content.stream()
                .map(Evaluation::getClassRoomId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> subjectIds = content.stream()
                .map(Evaluation::getSubjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> teacherIds = content.stream()
                .map(Evaluation::getTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, EvaluationPeriodSummary> periodsById = academicStructurePort.findEvaluationPeriodsByIds(periodIds);
        Map<UUID, ClassRoomSummary> roomsById = academicStructurePort.findClassRoomsByIds(classRoomIds);
        Map<UUID, SubjectSummary> subjectsById = academicStructurePort.findSubjectsByIds(subjectIds);
        Map<UUID, TeacherSummary> teachersById = teacherDirectoryPort.findTeachersByIds(teacherIds);

        return page.map(evaluation -> buildEvaluationView(
                evaluation,
                periodsById.get(evaluation.getEvaluationPeriodId()),
                roomsById.get(evaluation.getClassRoomId()),
                subjectsById.get(evaluation.getSubjectId()),
                evaluation.getTeacherId() != null ? teachersById.get(evaluation.getTeacherId()) : null));
    }

    private EvaluationView toEvaluationView(Evaluation evaluation) {
        EvaluationPeriodSummary period = academicStructurePort
                .findEvaluationPeriodById(evaluation.getEvaluationPeriodId()).orElse(null);
        ClassRoomSummary room = academicStructurePort.findClassRoomById(evaluation.getClassRoomId()).orElse(null);
        SubjectSummary subject = academicStructurePort.findSubjectById(evaluation.getSubjectId()).orElse(null);
        TeacherSummary teacher = evaluation.getTeacherId() != null
                ? teacherDirectoryPort.findTeacherById(evaluation.getTeacherId()).orElse(null)
                : null;
        return buildEvaluationView(evaluation, period, room, subject, teacher);
    }

    private static EvaluationView buildEvaluationView(
            Evaluation evaluation,
            EvaluationPeriodSummary period,
            ClassRoomSummary room,
            SubjectSummary subject,
            TeacherSummary teacher) {
        return new EvaluationView(
                evaluation,
                period != null ? period.code() : null,
                period != null ? period.status() : null,
                room != null ? room.code() : null,
                room != null ? room.name() : null,
                subject != null ? subject.code() : null,
                subject != null ? subject.name() : null,
                teacher != null ? teacher.firstName() + " " + teacher.lastName() : null);
    }

    private List<StudentGradeView> enrichGradeViews(List<StudentGrade> grades) {
        if (grades.isEmpty()) {
            return List.of();
        }
        Set<UUID> studentIds = grades.stream().map(StudentGrade::getStudentId).collect(Collectors.toSet());
        Map<UUID, StudentSummary> studentsById = new HashMap<>();
        for (StudentSummary s : studentDirectoryPort.findStudentsByIds(studentIds)) {
            studentsById.put(s.id(), s);
        }
        return grades.stream()
                .map(grade -> {
                    StudentSummary student = studentsById.get(grade.getStudentId());
                    return new StudentGradeView(
                            grade,
                            student != null ? student.matricule() : null,
                            student != null ? student.firstName() : null,
                            student != null ? student.lastName() : null);
                })
                .toList();
    }
}
