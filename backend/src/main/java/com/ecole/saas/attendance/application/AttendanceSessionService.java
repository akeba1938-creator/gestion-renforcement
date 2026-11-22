package com.ecole.saas.attendance.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.AcademicYearSummary;
import com.ecole.saas.academic.ClassRoomSummary;
import com.ecole.saas.academic.EvaluationPeriodSummary;
import com.ecole.saas.academic.SubjectSummary;
import com.ecole.saas.attendance.StudentAttendanceSummary;
import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.attendance.domain.AttendanceConflictException;
import com.ecole.saas.attendance.domain.AttendanceForbiddenException;
import com.ecole.saas.attendance.domain.AttendanceNotFoundException;
import com.ecole.saas.attendance.domain.AttendanceRecord;
import com.ecole.saas.attendance.domain.AttendanceRecordStatus;
import com.ecole.saas.attendance.domain.AttendanceSession;
import com.ecole.saas.attendance.domain.AttendanceSessionStatus;
import com.ecole.saas.attendance.domain.AttendanceSummaryCalculator;
import com.ecole.saas.attendance.domain.AttendanceTimeSlot;
import com.ecole.saas.attendance.domain.AttendanceValidationException;
import com.ecole.saas.attendance.infrastructure.AttendanceRecordRepository;
import com.ecole.saas.attendance.infrastructure.AttendanceSessionRepository;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentEnrollmentSummary;
import com.ecole.saas.student.StudentSummary;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import com.ecole.saas.teacher.TeacherSummary;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * Service applicatif des séances / feuilles d'appel, pointages de présence/retard/absence,
 * justifications et synthèses d'assiduité.
 */
@Service
public class AttendanceSessionService {

    public record AttendanceRecordInput(
            UUID studentId,
            AttendanceRecordStatus status,
            Integer lateMinutes,
            String notes) {
    }

    public record AttendanceRecordView(
            AttendanceRecord record,
            String studentMatricule,
            String studentFirstName,
            String studentLastName) {
    }

    public record AttendanceSessionView(
            AttendanceSession session,
            String classRoomCode,
            String classRoomName,
            String periodCode,
            String subjectCode,
            String subjectName,
            String teacherFullName,
            List<AttendanceRecordView> records) {
    }

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository recordRepository;
    private final AcademicStructurePort academicStructurePort;
    private final StudentDirectoryPort studentDirectoryPort;
    private final TeacherDirectoryPort teacherDirectoryPort;
    private final AttendanceSubscriptionGuard subscriptionGuard;
    private final AttendanceTeacherScopeGuard teacherScopeGuard;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public AttendanceSessionService(
            AttendanceSessionRepository sessionRepository,
            AttendanceRecordRepository recordRepository,
            AcademicStructurePort academicStructurePort,
            StudentDirectoryPort studentDirectoryPort,
            TeacherDirectoryPort teacherDirectoryPort,
            AttendanceSubscriptionGuard subscriptionGuard,
            AttendanceTeacherScopeGuard teacherScopeGuard,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.academicStructurePort = academicStructurePort;
        this.studentDirectoryPort = studentDirectoryPort;
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.subscriptionGuard = subscriptionGuard;
        this.teacherScopeGuard = teacherScopeGuard;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @PreAuthorize(AttendanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<AttendanceSessionView> listSessions(
            UUID academicYearId,
            UUID evaluationPeriodId,
            UUID classRoomId,
            UUID subjectId,
            LocalDate fromDate,
            LocalDate toDate,
            AttendanceSessionStatus status,
            Pageable pageable) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        Set<UUID> allowedClasses = null;
        if (teacherScopeGuard.isRestrictedTeacher()) {
            if (classRoomId != null) {
                teacherScopeGuard.assertCanAccessSessionScope(classRoomId, subjectId);
            }
            allowedClasses = teacherScopeGuard.currentTeacherClassRoomIds();
            if (allowedClasses.isEmpty()) {
                return Page.empty(pageable);
            }
        }
        Set<UUID> scopedClasses = allowedClasses;

        Specification<AttendanceSession> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (scopedClasses != null) {
                predicates.add(root.get("classRoomId").in(scopedClasses));
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
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sessionDate"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("sessionDate"), toDate));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return enrichSessionPage(sessionRepository.findAll(spec, pageable));
    }

    @PreAuthorize(AttendanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public AttendanceSessionView getSession(UUID sessionId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        AttendanceSession session = requireSession(sessionId);
        teacherScopeGuard.assertCanAccessClass(session.getClassRoomId());
        List<AttendanceRecord> records = recordRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return toSessionView(session, enrichRecordViews(records));
    }

    @PreAuthorize(AttendanceRoles.ROLL_CALL_WRITE_ROLES)
    @Transactional
    public AttendanceSessionView createSession(
            UUID classRoomId,
            UUID evaluationPeriodId,
            UUID subjectId,
            UUID teacherId,
            LocalDate sessionDate,
            AttendanceTimeSlot timeSlot,
            LocalTime startTime,
            LocalTime endTime,
            String label,
            String notes,
            boolean initializePresent,
            UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        ClassRoomSummary classRoom = academicStructurePort.findClassRoomById(classRoomId)
                .orElseThrow(() -> new AttendanceNotFoundException(
                        "classroom-not-found", "Classe introuvable", "La classe demandée est introuvable."));
        if (!"ACTIVE".equals(classRoom.status())) {
            throw new AttendanceConflictException(
                    "classroom-archived",
                    "Classe archivée",
                    "Impossible d'ouvrir une feuille d'appel sur une classe archivée.");
        }

        AcademicYearSummary year = academicStructurePort.findAcademicYearById(classRoom.academicYearId())
                .orElseThrow(() -> new AttendanceNotFoundException(
                        "academic-year-not-found", "Année scolaire introuvable", "Année scolaire introuvable."));
        assertYearMutable(year);

        if (sessionDate == null) {
            throw new AttendanceValidationException("La date de la séance d'appel est obligatoire.");
        }
        if (sessionDate.isBefore(year.startDate()) || sessionDate.isAfter(year.endDate())) {
            throw new AttendanceValidationException(
                    "La date de la séance d'appel doit être comprise dans l'année scolaire ("
                            + year.startDate() + " au " + year.endDate() + ").");
        }

        if (subjectId != null) {
            boolean subjectActiveOnClass = academicStructurePort.findActiveSubjectsForClass(classRoomId).stream()
                    .anyMatch(cs -> subjectId.equals(cs.subjectId()));
            if (!subjectActiveOnClass) {
                throw new AttendanceConflictException(
                        "class-subject-not-active",
                        "Matière non affectée à la classe",
                        "La matière demandée n'est pas activement affectée à cette classe.");
            }
        }

        teacherScopeGuard.assertCanAccessSessionScope(classRoomId, subjectId);

        UUID resolvedPeriodId = resolveEvaluationPeriodId(year.id(), evaluationPeriodId, sessionDate);
        UUID resolvedTeacherId = resolveTeacherId(teacherId);

        AttendanceSession saved = sessionRepository.save(new AttendanceSession(
                year.id(),
                resolvedPeriodId,
                classRoomId,
                subjectId,
                resolvedTeacherId,
                sessionDate,
                timeSlot,
                startTime,
                endTime,
                label,
                actorId,
                notes));

        List<AttendanceRecord> initialRecords = new ArrayList<>();
        if (initializePresent) {
            for (StudentEnrollmentSummary enrollment : studentDirectoryPort.findActiveEnrollmentsByClassRoom(classRoomId)) {
                initialRecords.add(new AttendanceRecord(
                        saved.getId(),
                        enrollment.studentId(),
                        AttendanceRecordStatus.PRESENT,
                        null,
                        null,
                        actorId));
            }
            if (!initialRecords.isEmpty()) {
                initialRecords = recordRepository.saveAll(initialRecords);
            }
        }

        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.ATTENDANCE,
                AuditAction.ATTENDANCE_SESSION_CREATED,
                "ATTENDANCE_SESSION",
                saved.getId(),
                "Ouverture d'une séance d'appel.",
                Map.of(
                        "classRoomId", classRoomId.toString(),
                        "sessionDate", sessionDate.toString(),
                        "timeSlot", timeSlot.name())));
        return toSessionView(saved, enrichRecordViews(initialRecords));
    }

    @PreAuthorize(AttendanceRoles.ROLL_CALL_WRITE_ROLES)
    @Transactional
    public AttendanceSessionView upsertRecords(
            UUID sessionId,
            List<AttendanceRecordInput> entries,
            UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        if (entries == null || entries.isEmpty()) {
            throw new AttendanceValidationException("Au moins un pointage élève doit être fourni.");
        }

        AttendanceSession session = requireSessionForUpdate(sessionId);
        teacherScopeGuard.assertCanAccessSessionScope(session.getClassRoomId(), session.getSubjectId());
        session.assertRollCallEditable();
        if (session.getStatus() == AttendanceSessionStatus.SUBMITTED && teacherScopeGuard.isRestrictedTeacher()) {
            throw new AttendanceForbiddenException(
                    "Une feuille d'appel déjà soumise ne peut plus être modifiée par un enseignant.");
        }
        assertPeriodNotLocked(session.getEvaluationPeriodId());

        Set<UUID> enrolledStudentIds = studentDirectoryPort
                .findActiveEnrollmentsByClassRoom(session.getClassRoomId())
                .stream()
                .map(StudentEnrollmentSummary::studentId)
                .collect(Collectors.toSet());

        Set<UUID> seenStudentIds = new HashSet<>();
        for (AttendanceRecordInput entry : entries) {
            if (entry == null || entry.studentId() == null) {
                throw new AttendanceValidationException("L'identifiant élève est obligatoire pour chaque pointage.");
            }
            if (!seenStudentIds.add(entry.studentId())) {
                throw new AttendanceValidationException("Doublon d'élève détecté dans la feuille d'appel.");
            }
            if (!enrolledStudentIds.contains(entry.studentId())) {
                if (studentDirectoryPort.findStudentById(entry.studentId()).isEmpty()) {
                    throw new AttendanceNotFoundException(
                            "student-not-found", "Élève introuvable", "L'élève demandé est introuvable.");
                }
                throw new AttendanceValidationException(
                        "L'élève indiqué ne possède pas d'inscription active dans cette classe.");
            }
        }

        Map<UUID, AttendanceRecord> existingByStudent = recordRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .collect(Collectors.toMap(AttendanceRecord::getStudentId, Function.identity()));

        List<AttendanceRecord> toSave = new ArrayList<>();
        for (AttendanceRecordInput entry : entries) {
            AttendanceRecord existing = existingByStudent.get(entry.studentId());
            if (existing != null) {
                existing.updateRecord(entry.status(), entry.lateMinutes(), entry.notes(), actorId);
                toSave.add(existing);
            } else {
                toSave.add(new AttendanceRecord(
                        sessionId,
                        entry.studentId(),
                        entry.status(),
                        entry.lateMinutes(),
                        entry.notes(),
                        actorId));
            }
        }
        recordRepository.saveAll(toSave);

        List<AttendanceRecord> refreshed = recordRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return toSessionView(session, enrichRecordViews(refreshed));
    }

    @PreAuthorize(AttendanceRoles.ROLL_CALL_WRITE_ROLES)
    @Transactional
    public AttendanceSessionView submitSession(UUID sessionId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        AttendanceSession session = requireSessionForUpdate(sessionId);
        teacherScopeGuard.assertCanAccessSessionScope(session.getClassRoomId(), session.getSubjectId());
        assertPeriodNotLocked(session.getEvaluationPeriodId());

        session.submit(clock.instant());
        AttendanceSession saved = sessionRepository.save(session);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.ATTENDANCE,
                AuditAction.ATTENDANCE_SESSION_SUBMITTED,
                "ATTENDANCE_SESSION",
                saved.getId(),
                "Soumission d'une feuille d'appel.",
                Map.of("classRoomId", saved.getClassRoomId().toString(), "sessionDate", saved.getSessionDate().toString())));
        List<AttendanceRecord> records = recordRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return toSessionView(saved, enrichRecordViews(records));
    }

    @PreAuthorize(AttendanceRoles.ADMIN_MANAGE_ROLES)
    @Transactional
    public AttendanceSessionView lockSession(UUID sessionId, UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        AttendanceSession session = requireSessionForUpdate(sessionId);
        session.lock(actorId, clock.instant());
        AttendanceSession saved = sessionRepository.save(session);
        auditLogPort.recordTenantEvent(AuditRecordCommand.critical(
                AuditCategory.ATTENDANCE,
                AuditAction.ATTENDANCE_SESSION_LOCKED,
                AuditOutcome.SUCCESS,
                "ATTENDANCE_SESSION",
                saved.getId(),
                "Verrouillage définitif d'une feuille d'appel.",
                Map.of("classRoomId", saved.getClassRoomId().toString(), "sessionDate", saved.getSessionDate().toString())));
        List<AttendanceRecord> records = recordRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return toSessionView(saved, enrichRecordViews(records));
    }

    @PreAuthorize(AttendanceRoles.ADMIN_MANAGE_ROLES)
    @Transactional
    public AttendanceSessionView cancelSession(UUID sessionId, String reason) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        AttendanceSession session = requireSessionForUpdate(sessionId);
        session.cancel(reason);
        AttendanceSession saved = sessionRepository.save(session);
        auditLogPort.recordTenantEvent(AuditRecordCommand.warning(
                AuditCategory.ATTENDANCE,
                AuditAction.ATTENDANCE_SESSION_CANCELLED,
                AuditOutcome.SUCCESS,
                "ATTENDANCE_SESSION",
                saved.getId(),
                "Annulation d'une feuille d'appel.",
                Map.of("classRoomId", saved.getClassRoomId().toString(), "sessionDate", saved.getSessionDate().toString())));
        List<AttendanceRecord> records = recordRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return toSessionView(saved, enrichRecordViews(records));
    }

    @PreAuthorize(AttendanceRoles.JUSTIFY_ROLES)
    @Transactional
    public AttendanceRecordView justifyRecord(
            UUID recordId,
            String reason,
            String documentRef,
            UUID actorId) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireWriteAccess();

        AttendanceRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new AttendanceNotFoundException(
                        "attendance-record-not-found",
                        "Pointage introuvable",
                        "L'enregistrement d'assiduité demandé est introuvable."));

        AttendanceSession session = requireSessionForUpdate(record.getSessionId());
        session.assertJustifiable();
        assertPeriodNotLocked(session.getEvaluationPeriodId());

        record.justify(reason, documentRef, actorId, clock.instant());
        AttendanceRecord saved = recordRepository.save(record);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.ATTENDANCE,
                AuditAction.ATTENDANCE_JUSTIFIED,
                "ATTENDANCE_RECORD",
                saved.getId(),
                "Justification d'une absence ou d'un retard élève.",
                Map.of(
                        "sessionId", saved.getSessionId().toString(),
                        "studentId", saved.getStudentId().toString())));
        return enrichRecordViews(List.of(saved)).getFirst();
    }

    @PreAuthorize(AttendanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public StudentAttendanceSummary getStudentSummary(
            UUID studentId,
            UUID academicYearId,
            UUID evaluationPeriodId,
            LocalDate fromDate,
            LocalDate toDate) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        if (studentDirectoryPort.findStudentById(studentId).isEmpty()) {
            throw new AttendanceNotFoundException(
                    "student-not-found", "Élève introuvable", "L'élève demandé est introuvable.");
        }

        if (teacherScopeGuard.isRestrictedTeacher()) {
            Set<UUID> allowedClasses = teacherScopeGuard.currentTeacherClassRoomIds();
            boolean studentInAllowedClass = allowedClasses.stream()
                    .flatMap(cid -> studentDirectoryPort.findActiveEnrollmentsByClassRoom(cid).stream())
                    .anyMatch(e -> studentId.equals(e.studentId()));
            if (!studentInAllowedClass) {
                throw new AttendanceForbiddenException(
                        "Vous ne pouvez consulter l'assiduité que des élèves inscrits dans vos classes affectées.");
            }
        }

        return computeStudentSummaryInternal(studentId, academicYearId, evaluationPeriodId, fromDate, toDate);
    }

    @PreAuthorize(AttendanceRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public List<StudentAttendanceSummary> getClassSummaries(
            UUID classRoomId,
            UUID evaluationPeriodId,
            LocalDate fromDate,
            LocalDate toDate) {
        TenantContext.requireCurrent();
        subscriptionGuard.requireReadAccess();

        ClassRoomSummary classRoom = academicStructurePort.findClassRoomById(classRoomId)
                .orElseThrow(() -> new AttendanceNotFoundException(
                        "classroom-not-found", "Classe introuvable", "La classe demandée est introuvable."));
        teacherScopeGuard.assertCanAccessClass(classRoomId);

        return computeClassSummariesInternal(classRoom, evaluationPeriodId, fromDate, toDate);
    }

    StudentAttendanceSummary computeStudentSummaryInternal(
            UUID studentId,
            UUID academicYearId,
            UUID evaluationPeriodId,
            LocalDate fromDate,
            LocalDate toDate) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new AttendanceValidationException("La date de fin doit être postérieure ou égale à la date de début.");
        }

        List<AttendanceSession> sessions;
        UUID effectiveYearId = academicYearId;
        if (evaluationPeriodId != null) {
            EvaluationPeriodSummary period = academicStructurePort.findEvaluationPeriodById(evaluationPeriodId)
                    .orElseThrow(() -> new AttendanceNotFoundException(
                            "evaluation-period-not-found",
                            "Période d'évaluation introuvable",
                            "La période d'évaluation demandée est introuvable."));
            effectiveYearId = period.academicYearId();
            sessions = sessionRepository.findByEvaluationPeriodIdAndStatusNot(
                    evaluationPeriodId, AttendanceSessionStatus.CANCELLED);
        } else if (academicYearId != null) {
            sessions = sessionRepository.findByAcademicYearIdAndStatusNot(
                    academicYearId, AttendanceSessionStatus.CANCELLED);
        } else {
            sessions = sessionRepository.findAll().stream()
                    .filter(s -> s.getStatus() != AttendanceSessionStatus.CANCELLED)
                    .toList();
        }

        List<UUID> sessionIds = sessions.stream()
                .filter(s -> fromDate == null || !s.getSessionDate().isBefore(fromDate))
                .filter(s -> toDate == null || !s.getSessionDate().isAfter(toDate))
                .map(AttendanceSession::getId)
                .toList();

        List<AttendanceRecord> records = sessionIds.isEmpty()
                ? List.of()
                : recordRepository.findByStudentIdAndSessionIdIn(studentId, sessionIds);

        AttendanceSummaryCalculator.AttendanceMetrics metrics = AttendanceSummaryCalculator.compute(records);
        return new StudentAttendanceSummary(
                studentId,
                effectiveYearId,
                evaluationPeriodId,
                fromDate,
                toDate,
                metrics.totalSessions(),
                metrics.presentCount(),
                metrics.lateCount(),
                metrics.totalLateMinutes(),
                metrics.absentCount(),
                metrics.justifiedAbsenceCount(),
                metrics.unjustifiedAbsenceCount(),
                metrics.excusedCount(),
                metrics.attendanceRatePercent());
    }

    List<StudentAttendanceSummary> computeClassSummariesInternal(
            ClassRoomSummary classRoom,
            UUID evaluationPeriodId,
            LocalDate fromDate,
            LocalDate toDate) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new AttendanceValidationException("La date de fin doit être postérieure ou égale à la date de début.");
        }

        List<AttendanceSession> sessions;
        if (evaluationPeriodId != null) {
            sessions = sessionRepository.findByClassRoomIdAndEvaluationPeriodIdAndStatusNot(
                    classRoom.id(), evaluationPeriodId, AttendanceSessionStatus.CANCELLED);
        } else if (fromDate != null && toDate != null) {
            sessions = sessionRepository.findByClassRoomIdAndSessionDateBetweenAndStatusNot(
                    classRoom.id(), fromDate, toDate, AttendanceSessionStatus.CANCELLED);
        } else {
            sessions = sessionRepository.findByAcademicYearIdAndStatusNot(
                    classRoom.academicYearId(), AttendanceSessionStatus.CANCELLED).stream()
                    .filter(s -> classRoom.id().equals(s.getClassRoomId()))
                    .toList();
        }

        List<UUID> sessionIds = sessions.stream()
                .filter(s -> fromDate == null || !s.getSessionDate().isBefore(fromDate))
                .filter(s -> toDate == null || !s.getSessionDate().isAfter(toDate))
                .map(AttendanceSession::getId)
                .toList();

        Map<UUID, List<AttendanceRecord>> recordsByStudent = sessionIds.isEmpty()
                ? Map.of()
                : recordRepository.findBySessionIdIn(sessionIds).stream()
                        .collect(Collectors.groupingBy(AttendanceRecord::getStudentId));

        List<StudentEnrollmentSummary> enrollments =
                studentDirectoryPort.findActiveEnrollmentsByClassRoom(classRoom.id());
        List<StudentAttendanceSummary> summaries = new ArrayList<>();
        for (StudentEnrollmentSummary enrollment : enrollments) {
            List<AttendanceRecord> studentRecords = recordsByStudent.getOrDefault(enrollment.studentId(), List.of());
            AttendanceSummaryCalculator.AttendanceMetrics metrics = AttendanceSummaryCalculator.compute(studentRecords);
            summaries.add(new StudentAttendanceSummary(
                    enrollment.studentId(),
                    classRoom.academicYearId(),
                    evaluationPeriodId,
                    fromDate,
                    toDate,
                    metrics.totalSessions(),
                    metrics.presentCount(),
                    metrics.lateCount(),
                    metrics.totalLateMinutes(),
                    metrics.absentCount(),
                    metrics.justifiedAbsenceCount(),
                    metrics.unjustifiedAbsenceCount(),
                    metrics.excusedCount(),
                    metrics.attendanceRatePercent()));
        }
        return summaries;
    }

    private UUID resolveEvaluationPeriodId(UUID academicYearId, UUID requestedPeriodId, LocalDate sessionDate) {
        if (requestedPeriodId != null) {
            EvaluationPeriodSummary period = academicStructurePort.findEvaluationPeriodById(requestedPeriodId)
                    .orElseThrow(() -> new AttendanceNotFoundException(
                            "evaluation-period-not-found",
                            "Période d'évaluation introuvable",
                            "La période d'évaluation demandée est introuvable."));
            if (!academicYearId.equals(period.academicYearId())) {
                throw new AttendanceValidationException(
                        "La période d'évaluation ne correspond pas à l'année scolaire de la classe.");
            }
            if (sessionDate.isBefore(period.startDate()) || sessionDate.isAfter(period.endDate())) {
                throw new AttendanceValidationException(
                        "La date de la séance d'appel doit être comprise dans la période d'évaluation.");
            }
            if ("LOCKED".equals(period.status())) {
                throw new AttendanceConflictException(
                        "evaluation-period-locked",
                        "Période verrouillée",
                        "Impossible d'ouvrir une séance d'appel sur une période verrouillée.");
            }
            return period.id();
        }
        return academicStructurePort.findEvaluationPeriodsByYear(academicYearId).stream()
                .filter(p -> !sessionDate.isBefore(p.startDate()) && !sessionDate.isAfter(p.endDate()))
                .findFirst()
                .map(period -> {
                    if ("LOCKED".equals(period.status())) {
                        throw new AttendanceConflictException(
                                "evaluation-period-locked",
                                "Période verrouillée",
                                "La date de la séance appartient à une période d'évaluation verrouillée.");
                    }
                    return period.id();
                })
                .orElse(null);
    }

    private UUID resolveTeacherId(UUID requestedTeacherId) {
        if (requestedTeacherId != null) {
            TeacherSummary teacher = teacherDirectoryPort.findTeacherById(requestedTeacherId)
                    .orElseThrow(() -> new AttendanceNotFoundException(
                            "teacher-not-found", "Enseignant introuvable", "L'enseignant indiqué est introuvable."));
            if (!teacher.active()) {
                throw new AttendanceConflictException(
                        "teacher-not-active", "Enseignant inactif", "L'enseignant indiqué n'est pas actif.");
            }
            return teacher.id();
        }
        return teacherScopeGuard.currentTeacherProfile()
                .filter(TeacherSummary::active)
                .map(TeacherSummary::id)
                .orElse(null);
    }

    private void assertPeriodNotLocked(UUID evaluationPeriodId) {
        if (evaluationPeriodId == null) {
            return;
        }
        academicStructurePort.findEvaluationPeriodById(evaluationPeriodId).ifPresent(period -> {
            if ("LOCKED".equals(period.status())) {
                throw new AttendanceConflictException(
                        "evaluation-period-locked",
                        "Période verrouillée",
                        "La période d'évaluation associée est verrouillée et n'accepte plus de modification.");
            }
        });
    }

    private static void assertYearMutable(AcademicYearSummary year) {
        if ("CLOSED".equals(year.status()) || "ARCHIVED".equals(year.status())) {
            throw new AttendanceConflictException(
                    "academic-year-immutable",
                    "Année scolaire figée",
                    "Impossible d'enregistrer une feuille d'appel sur une année scolaire clôturée ou archivée.");
        }
    }

    private AttendanceSession requireSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new AttendanceNotFoundException(
                        "attendance-session-not-found",
                        "Séance d'appel introuvable",
                        "La feuille d'appel demandée est introuvable."));
    }

    private AttendanceSession requireSessionForUpdate(UUID sessionId) {
        return sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new AttendanceNotFoundException(
                        "attendance-session-not-found",
                        "Séance d'appel introuvable",
                        "La feuille d'appel demandée est introuvable."));
    }

    private Page<AttendanceSessionView> enrichSessionPage(Page<AttendanceSession> page) {
        if (page.isEmpty()) {
            return page.map(s -> buildSessionView(s, null, null, null, null, List.of()));
        }
        List<AttendanceSession> content = page.getContent();
        Set<UUID> classRoomIds = content.stream()
                .map(AttendanceSession::getClassRoomId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> periodIds = content.stream()
                .map(AttendanceSession::getEvaluationPeriodId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> subjectIds = content.stream()
                .map(AttendanceSession::getSubjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> teacherIds = content.stream()
                .map(AttendanceSession::getTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, ClassRoomSummary> roomsById = academicStructurePort.findClassRoomsByIds(classRoomIds);
        Map<UUID, EvaluationPeriodSummary> periodsById = academicStructurePort.findEvaluationPeriodsByIds(periodIds);
        Map<UUID, SubjectSummary> subjectsById = academicStructurePort.findSubjectsByIds(subjectIds);
        Map<UUID, TeacherSummary> teachersById = teacherDirectoryPort.findTeachersByIds(teacherIds);

        return page.map(session -> buildSessionView(
                session,
                roomsById.get(session.getClassRoomId()),
                session.getEvaluationPeriodId() != null ? periodsById.get(session.getEvaluationPeriodId()) : null,
                session.getSubjectId() != null ? subjectsById.get(session.getSubjectId()) : null,
                session.getTeacherId() != null ? teachersById.get(session.getTeacherId()) : null,
                List.of()));
    }

    private AttendanceSessionView toSessionView(AttendanceSession session, List<AttendanceRecordView> records) {
        ClassRoomSummary room = academicStructurePort.findClassRoomById(session.getClassRoomId()).orElse(null);
        EvaluationPeriodSummary period = session.getEvaluationPeriodId() != null
                ? academicStructurePort.findEvaluationPeriodById(session.getEvaluationPeriodId()).orElse(null)
                : null;
        SubjectSummary subject = session.getSubjectId() != null
                ? academicStructurePort.findSubjectById(session.getSubjectId()).orElse(null)
                : null;
        TeacherSummary teacher = session.getTeacherId() != null
                ? teacherDirectoryPort.findTeacherById(session.getTeacherId()).orElse(null)
                : null;
        return buildSessionView(session, room, period, subject, teacher, records);
    }

    private static AttendanceSessionView buildSessionView(
            AttendanceSession session,
            ClassRoomSummary room,
            EvaluationPeriodSummary period,
            SubjectSummary subject,
            TeacherSummary teacher,
            List<AttendanceRecordView> records) {
        return new AttendanceSessionView(
                session,
                room != null ? room.code() : null,
                room != null ? room.name() : null,
                period != null ? period.code() : null,
                subject != null ? subject.code() : null,
                subject != null ? subject.name() : null,
                teacher != null ? teacher.firstName() + " " + teacher.lastName() : null,
                records);
    }

    private List<AttendanceRecordView> enrichRecordViews(List<AttendanceRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Set<UUID> studentIds = records.stream().map(AttendanceRecord::getStudentId).collect(Collectors.toSet());
        Map<UUID, StudentSummary> studentsById = new HashMap<>();
        for (StudentSummary student : studentDirectoryPort.findStudentsByIds(studentIds)) {
            studentsById.put(student.id(), student);
        }
        return records.stream()
                .map(record -> {
                    StudentSummary student = studentsById.get(record.getStudentId());
                    return new AttendanceRecordView(
                            record,
                            student != null ? student.matricule() : null,
                            student != null ? student.firstName() : null,
                            student != null ? student.lastName() : null);
                })
                .toList();
    }
}
