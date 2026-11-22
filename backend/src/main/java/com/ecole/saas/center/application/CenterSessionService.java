package com.ecole.saas.center.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.SubjectSummary;
import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.center.domain.CenterAttendanceStatus;
import com.ecole.saas.center.domain.CenterConflictException;
import com.ecole.saas.center.domain.CenterGroup;
import com.ecole.saas.center.domain.CenterGroupEnrollment;
import com.ecole.saas.center.domain.CenterGroupEnrollmentStatus;
import com.ecole.saas.center.domain.CenterGroupTutor;
import com.ecole.saas.center.domain.CenterNotFoundException;
import com.ecole.saas.center.domain.CenterPackConsumptionPolicy;
import com.ecole.saas.center.domain.CenterScheduleConflictChecker;
import com.ecole.saas.center.domain.CenterScheduleSlot;
import com.ecole.saas.center.domain.CenterSession;
import com.ecole.saas.center.domain.CenterSessionAttendance;
import com.ecole.saas.center.domain.CenterSessionGenerator;
import com.ecole.saas.center.domain.CenterSessionStatus;
import com.ecole.saas.center.domain.CenterStudentPack;
import com.ecole.saas.center.domain.CenterStudentPackStatus;
import com.ecole.saas.center.domain.CenterValidationException;
import com.ecole.saas.center.infrastructure.CenterGroupEnrollmentRepository;
import com.ecole.saas.center.infrastructure.CenterGroupRepository;
import com.ecole.saas.center.infrastructure.CenterGroupTutorRepository;
import com.ecole.saas.center.infrastructure.CenterScheduleSlotRepository;
import com.ecole.saas.center.infrastructure.CenterSessionAttendanceRepository;
import com.ecole.saas.center.infrastructure.CenterSessionRepository;
import com.ecole.saas.center.infrastructure.CenterStudentPackRepository;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentSummary;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import com.ecole.saas.teacher.TeacherSummary;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * Gestion des créneaux hebdomadaires, des séances de renforcement (unitaires ou générées en lot)
 * et du suivi individuel de présence/participation avec consommation/restitution de packs.
 */
@Service
public class CenterSessionService {

    private final CenterGroupRepository groupRepository;
    private final CenterGroupTutorRepository groupTutorRepository;
    private final CenterGroupEnrollmentRepository enrollmentRepository;
    private final CenterScheduleSlotRepository scheduleSlotRepository;
    private final CenterSessionRepository sessionRepository;
    private final CenterSessionAttendanceRepository attendanceRepository;
    private final CenterStudentPackRepository studentPackRepository;
    private final AcademicStructurePort academicStructurePort;
    private final TeacherDirectoryPort teacherDirectoryPort;
    private final StudentDirectoryPort studentDirectoryPort;
    private final CenterSubscriptionGuard subscriptionGuard;
    private final CenterSchoolGuard schoolGuard;
    private final CenterTutorScopeGuard tutorScopeGuard;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public CenterSessionService(
            CenterGroupRepository groupRepository,
            CenterGroupTutorRepository groupTutorRepository,
            CenterGroupEnrollmentRepository enrollmentRepository,
            CenterScheduleSlotRepository scheduleSlotRepository,
            CenterSessionRepository sessionRepository,
            CenterSessionAttendanceRepository attendanceRepository,
            CenterStudentPackRepository studentPackRepository,
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
        this.scheduleSlotRepository = scheduleSlotRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.studentPackRepository = studentPackRepository;
        this.academicStructurePort = academicStructurePort;
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.studentDirectoryPort = studentDirectoryPort;
        this.subscriptionGuard = subscriptionGuard;
        this.schoolGuard = schoolGuard;
        this.tutorScopeGuard = tutorScopeGuard;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    // --- Créneaux récurrents ---

    @PreAuthorize(CenterRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public List<CenterScheduleSlot> listScheduleSlots(UUID groupId) {
        subscriptionGuard.requireReadAccess();
        CenterGroup group = requireGroup(groupId);
        tutorScopeGuard.assertCanAccessGroup(group.getId());
        return scheduleSlotRepository.findByGroupIdOrderByActiveDescDayOfWeekAscStartTimeAsc(group.getId());
    }

    @PreAuthorize(CenterRoles.GROUP_MANAGE_ROLES)
    @Transactional
    public CenterScheduleSlot createScheduleSlot(
            UUID groupId,
            UUID teacherId,
            UUID subjectId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            String roomName,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterGroup group = requireGroup(groupId);
        if (!group.isOperationalForEnrollmentsAndPlanning()) {
            throw new CenterConflictException(
                    "center-group-immutable",
                    "Cohorte non opérationnelle",
                    "Impossible de créer un créneau sur une cohorte clôturée ou archivée.");
        }
        validateOptionalTeacherAndSubject(teacherId, subjectId);

        CenterScheduleSlot candidate = new CenterScheduleSlot(
                groupId, teacherId, subjectId, dayOfWeek, startTime, endTime, roomName, effectiveFrom, effectiveUntil);
        assertNoSlotConflicts(null, groupId, teacherId, dayOfWeek, startTime, endTime, effectiveFrom, effectiveUntil);
        return scheduleSlotRepository.save(candidate);
    }

    @PreAuthorize(CenterRoles.GROUP_MANAGE_ROLES)
    @Transactional
    public CenterScheduleSlot updateScheduleSlot(
            UUID slotId,
            UUID teacherId,
            UUID subjectId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            String roomName,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterScheduleSlot slot = requireScheduleSlot(slotId);
        validateOptionalTeacherAndSubject(teacherId, subjectId);
        assertNoSlotConflicts(
                slot.getId(),
                slot.getGroupId(),
                teacherId,
                dayOfWeek,
                startTime,
                endTime,
                effectiveFrom,
                effectiveUntil);
        slot.update(teacherId, subjectId, dayOfWeek, startTime, endTime, roomName, effectiveFrom, effectiveUntil);
        return scheduleSlotRepository.save(slot);
    }

    @PreAuthorize(CenterRoles.GROUP_MANAGE_ROLES)
    @Transactional
    public CenterScheduleSlot deactivateScheduleSlot(UUID slotId) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterScheduleSlot slot = requireScheduleSlot(slotId);
        slot.deactivate();
        return scheduleSlotRepository.save(slot);
    }

    // --- Séances datées ---

    @PreAuthorize(CenterRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public Page<SessionView> listSessions(
            UUID groupId,
            UUID teacherId,
            LocalDate fromDate,
            LocalDate toDate,
            CenterSessionStatus status,
            Pageable pageable) {
        subscriptionGuard.requireReadAccess();
        boolean restrictedTutor = tutorScopeGuard.isRestrictedTutor();
        UUID currentTutorTeacherId = restrictedTutor
                ? tutorScopeGuard.currentTutorProfile().map(TeacherSummary::id).orElse(null)
                : null;
        Set<UUID> restrictedGroupIds = restrictedTutor ? tutorScopeGuard.currentTutorGroupIds() : null;

        if (restrictedTutor && currentTutorTeacherId == null && (restrictedGroupIds == null || restrictedGroupIds.isEmpty())) {
            return Page.empty(pageable);
        }

        Specification<CenterSession> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (restrictedTutor) {
                List<Predicate> tutorPredicates = new ArrayList<>();
                if (currentTutorTeacherId != null) {
                    tutorPredicates.add(cb.equal(root.get("teacherId"), currentTutorTeacherId));
                }
                if (restrictedGroupIds != null && !restrictedGroupIds.isEmpty()) {
                    tutorPredicates.add(root.get("groupId").in(restrictedGroupIds));
                }
                predicates.add(cb.or(tutorPredicates.toArray(Predicate[]::new)));
            }
            if (groupId != null) {
                predicates.add(cb.equal(root.get("groupId"), groupId));
            }
            if (teacherId != null) {
                predicates.add(cb.equal(root.get("teacherId"), teacherId));
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
        return sessionRepository.findAll(spec, pageable).map(s -> toSessionView(s, false));
    }

    @PreAuthorize(CenterRoles.READ_ROLES)
    @Transactional(readOnly = true)
    public SessionView getSession(UUID sessionId) {
        subscriptionGuard.requireReadAccess();
        CenterSession session = requireSession(sessionId);
        tutorScopeGuard.assertCanAccessSession(session);
        return toSessionView(session, true);
    }

    @PreAuthorize(CenterRoles.GROUP_MANAGE_ROLES)
    @Transactional
    public SessionView createSession(
            UUID groupId,
            UUID scheduleSlotId,
            UUID teacherId,
            UUID subjectId,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            String roomName,
            String topic,
            String notes,
            UUID actorId) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterGroup group = requireGroup(groupId);
        if (!group.isOperationalForEnrollmentsAndPlanning()) {
            throw new CenterConflictException(
                    "center-group-immutable",
                    "Cohorte non opérationnelle",
                    "Impossible de planifier une séance sur une cohorte clôturée ou archivée.");
        }
        if (scheduleSlotId != null) {
            CenterScheduleSlot slot = requireScheduleSlot(scheduleSlotId);
            if (!groupId.equals(slot.getGroupId())) {
                throw new CenterValidationException(
                        "center-session-invalid",
                        "Créneau incohérent",
                        "Le créneau référencé n'appartient pas à cette cohorte.");
            }
        }
        validateOptionalTeacherAndSubject(teacherId, subjectId);
        UUID resolvedSubject = subjectId != null ? subjectId : group.getSubjectId();
        String resolvedRoom = (roomName != null && !roomName.isBlank()) ? roomName : group.getRoomName();

        CenterSession candidate = new CenterSession(
                groupId,
                scheduleSlotId,
                teacherId,
                resolvedSubject,
                sessionDate,
                startTime,
                endTime,
                resolvedRoom,
                topic,
                notes,
                actorId);
        assertNoSessionConflicts(null, groupId, teacherId, sessionDate, startTime, endTime);
        return toSessionView(sessionRepository.save(candidate), true);
    }

    @PreAuthorize(CenterRoles.GROUP_MANAGE_ROLES)
    @Transactional
    public GeneratedSessionsResult generateSessions(
            UUID groupId, LocalDate fromDate, LocalDate toDate, UUID actorId) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterGroup group = requireGroup(groupId);
        if (!group.isOperationalForEnrollmentsAndPlanning()) {
            throw new CenterConflictException(
                    "center-group-immutable",
                    "Cohorte non opérationnelle",
                    "Impossible de générer des séances pour une cohorte clôturée ou archivée.");
        }
        List<CenterScheduleSlot> activeSlots = scheduleSlotRepository.findByGroupIdAndActiveTrue(groupId);
        List<CenterSessionGenerator.PlannedOccurrence> occurrences =
                CenterSessionGenerator.generateOccurrences(group, activeSlots, fromDate, toDate);
        if (occurrences.isEmpty()) {
            return new GeneratedSessionsResult(List.of(), 0);
        }

        List<CenterSession> existingGroupSessions = sessionRepository
                .findByGroupIdAndSessionDateBetweenAndStatusNot(
                        groupId, fromDate, toDate, CenterSessionStatus.CANCELLED);
        Map<LocalDate, List<CenterSession>> groupSessionsByDate = new HashMap<>();
        for (CenterSession existing : existingGroupSessions) {
            groupSessionsByDate
                    .computeIfAbsent(existing.getSessionDate(), ignored -> new ArrayList<>())
                    .add(existing);
        }

        Set<UUID> teacherIds = occurrences.stream()
                .map(CenterSessionGenerator.PlannedOccurrence::teacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Map<LocalDate, List<CenterSession>>> teacherSessionsByTeacherAndDate = new HashMap<>();
        if (!teacherIds.isEmpty()) {
            List<CenterSession> existingTeacherSessions = sessionRepository
                    .findByTeacherIdInAndSessionDateBetweenAndStatusNot(
                            teacherIds, fromDate, toDate, CenterSessionStatus.CANCELLED);
            for (CenterSession existing : existingTeacherSessions) {
                if (existing.getTeacherId() != null) {
                    teacherSessionsByTeacherAndDate
                            .computeIfAbsent(existing.getTeacherId(), ignored -> new HashMap<>())
                            .computeIfAbsent(existing.getSessionDate(), ignored -> new ArrayList<>())
                            .add(existing);
                }
            }
        }

        List<CenterSession> toCreate = new ArrayList<>();
        int skipped = 0;
        for (CenterSessionGenerator.PlannedOccurrence occ : occurrences) {
            List<CenterSession> sameDateGroupSessions = groupSessionsByDate.computeIfAbsent(
                    occ.sessionDate(), ignored -> new ArrayList<>());
            boolean alreadyGenerated = sameDateGroupSessions.stream()
                    .anyMatch(s -> occ.sessionDate().equals(s.getSessionDate())
                            && occ.startTime().equals(s.getStartTime())
                            && occ.endTime().equals(s.getEndTime()));
            if (alreadyGenerated) {
                skipped++;
                continue;
            }
            CenterScheduleConflictChecker.assertNoSessionConflict(
                    null,
                    occ.sessionDate(),
                    occ.startTime(),
                    occ.endTime(),
                    sameDateGroupSessions,
                    "La cohorte possède déjà une séance sur cet horaire à la date du " + occ.sessionDate() + ".");
            List<CenterSession> sameDateTeacherSessions = null;
            if (occ.teacherId() != null) {
                sameDateTeacherSessions = teacherSessionsByTeacherAndDate
                        .computeIfAbsent(occ.teacherId(), ignored -> new HashMap<>())
                        .computeIfAbsent(occ.sessionDate(), ignored -> new ArrayList<>());
                CenterScheduleConflictChecker.assertNoSessionConflict(
                        null,
                        occ.sessionDate(),
                        occ.startTime(),
                        occ.endTime(),
                        sameDateTeacherSessions,
                        "L'intervenant anime déjà une autre séance sur cet horaire à la date du "
                                + occ.sessionDate() + ".");
            }
            CenterSession session = new CenterSession(
                    groupId,
                    occ.scheduleSlotId(),
                    occ.teacherId(),
                    occ.subjectId(),
                    occ.sessionDate(),
                    occ.startTime(),
                    occ.endTime(),
                    occ.roomName(),
                    null,
                    null,
                    actorId);
            toCreate.add(session);
            sameDateGroupSessions.add(session);
            if (sameDateTeacherSessions != null) {
                sameDateTeacherSessions.add(session);
            }
        }
        List<CenterSession> created = toCreate.isEmpty() ? List.of() : sessionRepository.saveAll(toCreate);
        List<SessionView> views = created.stream().map(s -> toSessionView(s, false)).toList();
        return new GeneratedSessionsResult(views, skipped);
    }

    @PreAuthorize(CenterRoles.SESSION_CONDUCT_ROLES)
    @Transactional
    public SessionView startSession(UUID sessionId) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterSession session = requireSessionForUpdate(sessionId);
        tutorScopeGuard.assertCanAccessSession(session);
        session.start();
        CenterSession saved = sessionRepository.save(session);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.CENTER,
                AuditAction.CENTER_SESSION_STARTED,
                "CENTER_SESSION",
                saved.getId(),
                "Démarrage d'une séance de renforcement.",
                Map.of("groupId", saved.getGroupId().toString(), "sessionDate", saved.getSessionDate().toString())));
        return toSessionView(saved, true);
    }

    @PreAuthorize(CenterRoles.SESSION_CONDUCT_ROLES)
    @Transactional
    public SessionView completeSession(UUID sessionId, String topic, String notes, UUID actorId) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterSession session = requireSessionForUpdate(sessionId);
        tutorScopeGuard.assertCanAccessSession(session);
        Long hourlyRateSnapshot = resolveHourlyRateSnapshot(session);
        session.complete(actorId, clock.instant(), topic, notes, hourlyRateSnapshot);
        CenterSession saved = sessionRepository.save(session);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.CENTER,
                AuditAction.CENTER_SESSION_COMPLETED,
                "CENTER_SESSION",
                saved.getId(),
                "Clôture d'une séance de renforcement.",
                Map.of("groupId", saved.getGroupId().toString(), "sessionDate", saved.getSessionDate().toString())));
        return toSessionView(saved, true);
    }

    private Long resolveHourlyRateSnapshot(CenterSession session) {
        if (session.getTeacherId() == null) {
            return null;
        }
        List<CenterGroupTutor> assignments = groupTutorRepository
                .findByGroupIdAndTeacherIdOrderByActiveDescUpdatedAtDesc(
                        session.getGroupId(), session.getTeacherId());
        if (session.getSubjectId() != null) {
            for (CenterGroupTutor assignment : assignments) {
                if (assignment.isActive()
                        && Objects.equals(session.getSubjectId(), assignment.getSubjectId())
                        && assignment.getHourlyRateXof() != null) {
                    return assignment.getHourlyRateXof();
                }
            }
        }
        for (CenterGroupTutor assignment : assignments) {
            if (assignment.isActive() && assignment.getHourlyRateXof() != null) {
                return assignment.getHourlyRateXof();
            }
        }
        for (CenterGroupTutor assignment : assignments) {
            if (assignment.getHourlyRateXof() != null) {
                return assignment.getHourlyRateXof();
            }
        }
        return teacherDirectoryPort.findTeacherById(session.getTeacherId())
                .map(TeacherSummary::hourlyRateXof)
                .orElse(null);
    }

    @PreAuthorize(CenterRoles.GROUP_MANAGE_ROLES)
    @Transactional
    public SessionView cancelSession(UUID sessionId, String reason) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterSession session = requireSessionForUpdate(sessionId);
        session.cancel(reason);

        int refundedCredits = 0;
        List<CenterSessionAttendance> existingAttendances =
                attendanceRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        Set<UUID> packIdsToRefund = existingAttendances.stream()
                .map(CenterSessionAttendance::getConsumedStudentPackId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!packIdsToRefund.isEmpty()) {
            Map<UUID, CenterStudentPack> lockedPacksById = studentPackRepository.findByIdInForUpdate(packIdsToRefund)
                    .stream()
                    .collect(Collectors.toMap(CenterStudentPack::getId, Function.identity()));
            Map<UUID, CenterStudentPack> packsToSave = new LinkedHashMap<>();
            List<CenterSessionAttendance> attendancesToSave = new ArrayList<>();
            for (CenterSessionAttendance attendance : existingAttendances) {
                UUID consumedPackId = attendance.getConsumedStudentPackId();
                if (consumedPackId != null) {
                    CenterStudentPack pack = lockedPacksById.get(consumedPackId);
                    if (pack != null) {
                        CenterPackConsumptionPolicy.refundOnSessionCancellation(
                                attendance, pack, session.getSessionDate());
                        packsToSave.put(pack.getId(), pack);
                        attendancesToSave.add(attendance);
                    }
                    refundedCredits++;
                }
            }
            if (!packsToSave.isEmpty()) {
                studentPackRepository.saveAll(packsToSave.values());
            }
            if (!attendancesToSave.isEmpty()) {
                attendanceRepository.saveAll(attendancesToSave);
            }
        }
        CenterSession saved = sessionRepository.save(session);
        auditLogPort.recordTenantEvent(AuditRecordCommand.warning(
                AuditCategory.CENTER,
                AuditAction.CENTER_SESSION_CANCELLED,
                AuditOutcome.SUCCESS,
                "CENTER_SESSION",
                saved.getId(),
                "Annulation d'une séance de renforcement.",
                Map.of(
                        "groupId", saved.getGroupId().toString(),
                        "sessionDate", saved.getSessionDate().toString(),
                        "refundedCredits", refundedCredits)));
        return toSessionView(saved, true);
    }

    // --- Pointage de participation & consommation de pack ---

    @PreAuthorize(CenterRoles.SESSION_CONDUCT_ROLES)
    @Transactional
    public SessionView upsertAttendance(UUID sessionId, List<AttendanceInput> inputs, UUID actorId) {
        subscriptionGuard.requireWriteAccess();
        schoolGuard.requireActiveSchoolForWrite();
        CenterSession session = requireSessionForUpdate(sessionId);
        tutorScopeGuard.assertCanAccessSession(session);
        if (!session.isAttendanceMutable()) {
            throw new CenterConflictException(
                    "center-session-immutable",
                    "Séance verrouillée",
                    "Le pointage est impossible sur une séance clôturée ou annulée (statut : "
                            + session.getStatus() + ").");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new CenterValidationException(
                    "center-attendance-invalid",
                    "Pointage vide",
                    "Au moins un enregistrement de présence est requis.");
        }

        Set<UUID> seenStudentIds = new HashSet<>();
        for (AttendanceInput input : inputs) {
            if (input.studentId() == null) {
                throw new CenterValidationException(
                        "center-attendance-invalid", "Élève manquant", "Chaque ligne de pointage doit indiquer studentId.");
            }
            if (!seenStudentIds.add(input.studentId())) {
                throw new CenterValidationException(
                        "center-attendance-invalid",
                        "Doublon dans le pointage",
                        "Un même élève ne peut pas apparaître plusieurs fois dans le même appel.");
            }
        }

        Map<UUID, CenterGroupEnrollment> activeEnrollmentsByStudent =
                enrollmentRepository.findByGroupIdAndStatus(session.getGroupId(), CenterGroupEnrollmentStatus.ACTIVE)
                        .stream()
                        .collect(Collectors.toMap(CenterGroupEnrollment::getStudentId, Function.identity()));

        for (AttendanceInput input : inputs) {
            if (!activeEnrollmentsByStudent.containsKey(input.studentId())) {
                throw new CenterConflictException(
                        "center-student-not-enrolled",
                        "Élève non inscrit à la cohorte",
                        "L'élève " + input.studentId() + " ne dispose pas d'une inscription ACTIVE dans cette cohorte.");
            }
        }

        Map<UUID, CenterSessionAttendance> existingAttendancesByStudent =
                attendanceRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                        .stream()
                        .collect(Collectors.toMap(CenterSessionAttendance::getStudentId, Function.identity()));

        Set<UUID> fallbackStudentIds = new HashSet<>();
        for (AttendanceInput input : inputs) {
            CenterGroupEnrollment enrollment = activeEnrollmentsByStudent.get(input.studentId());
            if (enrollment.getStudentPackId() == null) {
                fallbackStudentIds.add(input.studentId());
            }
        }
        Map<UUID, List<CenterStudentPackRepository.ActivePackCandidate>> candidatesByStudent = new HashMap<>();
        if (!fallbackStudentIds.isEmpty()) {
            for (CenterStudentPackRepository.ActivePackCandidate candidate :
                    studentPackRepository.findActiveCandidatesByStudentIdInAndStatus(
                            fallbackStudentIds, CenterStudentPackStatus.ACTIVE)) {
                candidatesByStudent
                        .computeIfAbsent(candidate.getStudentId(), ignored -> new ArrayList<>())
                        .add(candidate);
            }
        }

        Map<UUID, UUID> eligiblePackIdByStudent = new HashMap<>();
        Set<UUID> packIdsToLock = new HashSet<>();
        for (AttendanceInput input : inputs) {
            CenterSessionAttendance existing = existingAttendancesByStudent.get(input.studentId());
            if (existing != null && existing.getConsumedStudentPackId() != null) {
                packIdsToLock.add(existing.getConsumedStudentPackId());
            }
            CenterGroupEnrollment enrollment = activeEnrollmentsByStudent.get(input.studentId());
            UUID eligiblePackId = resolveEligiblePackId(
                    enrollment,
                    session.getGroupId(),
                    session.getSessionDate(),
                    candidatesByStudent.getOrDefault(input.studentId(), List.of()));
            if (eligiblePackId != null) {
                eligiblePackIdByStudent.put(input.studentId(), eligiblePackId);
                packIdsToLock.add(eligiblePackId);
            }
        }

        Map<UUID, CenterStudentPack> lockedPacksById = packIdsToLock.isEmpty()
                ? Map.of()
                : studentPackRepository.findByIdInForUpdate(packIdsToLock)
                        .stream()
                        .collect(Collectors.toMap(CenterStudentPack::getId, Function.identity()));

        Map<UUID, CenterStudentPack> packsToSave = new LinkedHashMap<>();
        List<CenterSessionAttendance> attendancesToSave = new ArrayList<>(inputs.size());
        for (AttendanceInput input : inputs) {
            CenterGroupEnrollment enrollment = activeEnrollmentsByStudent.get(input.studentId());
            CenterSessionAttendance attendance = existingAttendancesByStudent.get(input.studentId());
            UUID previousPackId = attendance != null ? attendance.getConsumedStudentPackId() : null;

            if (attendance == null) {
                attendance = new CenterSessionAttendance(
                        sessionId,
                        input.studentId(),
                        enrollment.getId(),
                        input.status(),
                        input.lateMinutes(),
                        input.participationRating(),
                        input.homeworkDone(),
                        input.remarks(),
                        actorId);
            } else {
                attendance.updateAttendance(
                        input.status(),
                        input.lateMinutes(),
                        input.participationRating(),
                        input.homeworkDone(),
                        input.remarks(),
                        actorId);
            }

            CenterStudentPack previousPack = previousPackId != null ? lockedPacksById.get(previousPackId) : null;
            UUID eligiblePackId = eligiblePackIdByStudent.get(input.studentId());
            CenterStudentPack eligiblePack = eligiblePackId != null ? lockedPacksById.get(eligiblePackId) : null;

            CenterPackConsumptionPolicy.reconcileAttendancePack(
                    attendance, previousPack, eligiblePack, session.getSessionDate());

            if (previousPack != null) {
                packsToSave.put(previousPack.getId(), previousPack);
            }
            if (eligiblePack != null) {
                packsToSave.put(eligiblePack.getId(), eligiblePack);
            }
            attendancesToSave.add(attendance);
        }

        if (!packsToSave.isEmpty()) {
            studentPackRepository.saveAll(packsToSave.values());
        }
        attendanceRepository.saveAll(attendancesToSave);

        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.CENTER,
                AuditAction.CENTER_ATTENDANCE_RECORDED,
                "CENTER_SESSION",
                sessionId,
                "Enregistrement du pointage de participation d'une séance de renforcement.",
                Map.of(
                        "groupId", session.getGroupId().toString(),
                        "recordedCount", inputs.size())));

        return toSessionView(session, true);
    }

    private static UUID resolveEligiblePackId(
            CenterGroupEnrollment enrollment,
            UUID groupId,
            LocalDate sessionDate,
            List<CenterStudentPackRepository.ActivePackCandidate> activeCandidates) {
        if (enrollment.getStudentPackId() != null) {
            return enrollment.getStudentPackId();
        }
        // Préférer un pack explicitement lié à la cohorte, sinon un pack global élève valide à la date de séance
        return activeCandidates.stream()
                .filter(p -> groupId.equals(p.getGroupId()) && isCandidateValidOnDate(p, sessionDate))
                .map(CenterStudentPackRepository.ActivePackCandidate::getId)
                .findFirst()
                .or(() -> activeCandidates.stream()
                        .filter(p -> p.getGroupId() == null && isCandidateValidOnDate(p, sessionDate))
                        .map(CenterStudentPackRepository.ActivePackCandidate::getId)
                        .findFirst())
                .orElse(null);
    }

    private static boolean isCandidateValidOnDate(
            CenterStudentPackRepository.ActivePackCandidate candidate, LocalDate sessionDate) {
        if (sessionDate == null || candidate.getStartDate() == null) {
            return false;
        }
        if (sessionDate.isBefore(candidate.getStartDate())) {
            return false;
        }
        return candidate.getEndDate() == null || !sessionDate.isAfter(candidate.getEndDate());
    }

    private void assertNoSlotConflicts(
            UUID excludedSlotId,
            UUID groupId,
            UUID teacherId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil) {
        List<CenterScheduleSlot> groupSlots =
                scheduleSlotRepository.findByGroupIdAndDayOfWeekAndActiveTrue(groupId, dayOfWeek);
        CenterScheduleConflictChecker.assertNoSlotConflict(
                excludedSlotId,
                dayOfWeek,
                startTime,
                endTime,
                effectiveFrom,
                effectiveUntil,
                groupSlots,
                "La cohorte possède déjà un créneau actif qui chevauche cet horaire.");
        if (teacherId != null) {
            List<CenterScheduleSlot> teacherSlots =
                    scheduleSlotRepository.findByTeacherIdAndDayOfWeekAndActiveTrue(teacherId, dayOfWeek);
            CenterScheduleConflictChecker.assertNoSlotConflict(
                    excludedSlotId,
                    dayOfWeek,
                    startTime,
                    endTime,
                    effectiveFrom,
                    effectiveUntil,
                    teacherSlots,
                    "L'intervenant est déjà planifié sur un autre créneau actif qui chevauche cet horaire.");
        }
    }

    private void assertNoSessionConflicts(
            UUID excludedSessionId,
            UUID groupId,
            UUID teacherId,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime) {
        List<CenterSession> groupSessions = sessionRepository
                .findByGroupIdAndSessionDateAndStatusNot(groupId, sessionDate, CenterSessionStatus.CANCELLED);
        CenterScheduleConflictChecker.assertNoSessionConflict(
                excludedSessionId,
                sessionDate,
                startTime,
                endTime,
                groupSessions,
                "La cohorte possède déjà une séance sur cet horaire à la date du " + sessionDate + ".");
        if (teacherId != null) {
            List<CenterSession> teacherSessions = sessionRepository
                    .findByTeacherIdAndSessionDateAndStatusNot(teacherId, sessionDate, CenterSessionStatus.CANCELLED);
            CenterScheduleConflictChecker.assertNoSessionConflict(
                    excludedSessionId,
                    sessionDate,
                    startTime,
                    endTime,
                    teacherSessions,
                    "L'intervenant anime déjà une autre séance sur cet horaire à la date du " + sessionDate + ".");
        }
    }

    private void validateOptionalTeacherAndSubject(UUID teacherId, UUID subjectId) {
        if (teacherId != null) {
            TeacherSummary teacher = teacherDirectoryPort.findTeacherById(teacherId)
                    .orElseThrow(() -> new CenterNotFoundException(
                            "center-teacher-not-found", "Intervenant introuvable", "L'intervenant référencé est introuvable."));
            if (!teacher.active()) {
                throw new CenterConflictException(
                        "center-teacher-inactive", "Intervenant inactif", "L'intervenant référencé n'est pas actif.");
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

    private CenterGroup requireGroup(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new CenterNotFoundException(
                        "center-group-not-found", "Cohorte introuvable", "La cohorte demandée est introuvable."));
    }

    private CenterScheduleSlot requireScheduleSlot(UUID slotId) {
        return scheduleSlotRepository.findById(slotId)
                .orElseThrow(() -> new CenterNotFoundException(
                        "center-slot-not-found", "Créneau introuvable", "Le créneau demandé est introuvable."));
    }

    private CenterSession requireSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CenterNotFoundException(
                        "center-session-not-found", "Séance introuvable", "La séance demandée est introuvable."));
    }

    private CenterSession requireSessionForUpdate(UUID sessionId) {
        return sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new CenterNotFoundException(
                        "center-session-not-found", "Séance introuvable", "La séance demandée est introuvable."));
    }

    private SessionView toSessionView(CenterSession session, boolean includeAttendances) {
        if (!includeAttendances) {
            return new SessionView(session, List.of());
        }
        List<CenterSessionAttendance> rows =
                attendanceRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        Set<UUID> studentIds = rows.stream()
                .map(CenterSessionAttendance::getStudentId)
                .collect(Collectors.toSet());
        Map<UUID, StudentSummary> studentsById = studentIds.isEmpty()
                ? Map.of()
                : studentDirectoryPort.findStudentsByIds(studentIds).stream()
                        .collect(Collectors.toMap(StudentSummary::id, Function.identity()));
        List<AttendanceView> views = rows.stream()
                .map(r -> new AttendanceView(r, studentsById.get(r.getStudentId())))
                .toList();
        return new SessionView(session, views);
    }

    public record AttendanceInput(
            UUID studentId,
            CenterAttendanceStatus status,
            Integer lateMinutes,
            Integer participationRating,
            Boolean homeworkDone,
            String remarks) {
    }

    public record AttendanceView(CenterSessionAttendance attendance, StudentSummary student) {
    }

    public record SessionView(CenterSession session, List<AttendanceView> attendances) {
    }

    public record GeneratedSessionsResult(List<SessionView> sessions, int skippedCount) {
    }
}
