package com.ecole.saas.center.application;

import com.ecole.saas.center.CenterDirectoryPort;
import com.ecole.saas.center.CenterGroupEnrollmentSummary;
import com.ecole.saas.center.CenterGroupSummary;
import com.ecole.saas.center.CenterSessionPackSummary;
import com.ecole.saas.center.StudentCenterSummary;
import com.ecole.saas.center.TutorCenterWorkloadSummary;
import com.ecole.saas.center.domain.CenterGroup;
import com.ecole.saas.center.domain.CenterGroupEnrollment;
import com.ecole.saas.center.domain.CenterGroupEnrollmentStatus;
import com.ecole.saas.center.domain.CenterGroupStatus;
import com.ecole.saas.center.domain.CenterGroupTutor;
import com.ecole.saas.center.domain.CenterParticipationCalculator;
import com.ecole.saas.center.domain.CenterSession;
import com.ecole.saas.center.domain.CenterSessionAttendance;
import com.ecole.saas.center.domain.CenterSessionPack;
import com.ecole.saas.center.domain.CenterSessionStatus;
import com.ecole.saas.center.domain.CenterStudentPack;
import com.ecole.saas.center.domain.CenterStudentPackStatus;
import com.ecole.saas.center.infrastructure.CenterGroupEnrollmentRepository;
import com.ecole.saas.center.infrastructure.CenterGroupRepository;
import com.ecole.saas.center.infrastructure.CenterGroupTutorRepository;
import com.ecole.saas.center.infrastructure.CenterSessionAttendanceRepository;
import com.ecole.saas.center.infrastructure.CenterSessionPackRepository;
import com.ecole.saas.center.infrastructure.CenterSessionRepository;
import com.ecole.saas.center.infrastructure.CenterStudentPackRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation de la frontière publique Spring Modulith {@link CenterDirectoryPort}.
 */
@Service
@Transactional(readOnly = true)
class CenterDirectoryQueryService implements CenterDirectoryPort {

    private final CenterGroupRepository groupRepository;
    private final CenterGroupTutorRepository groupTutorRepository;
    private final CenterGroupEnrollmentRepository enrollmentRepository;
    private final CenterSessionPackRepository packRepository;
    private final CenterStudentPackRepository studentPackRepository;
    private final CenterSessionRepository sessionRepository;
    private final CenterSessionAttendanceRepository attendanceRepository;

    CenterDirectoryQueryService(
            CenterGroupRepository groupRepository,
            CenterGroupTutorRepository groupTutorRepository,
            CenterGroupEnrollmentRepository enrollmentRepository,
            CenterSessionPackRepository packRepository,
            CenterStudentPackRepository studentPackRepository,
            CenterSessionRepository sessionRepository,
            CenterSessionAttendanceRepository attendanceRepository) {
        this.groupRepository = groupRepository;
        this.groupTutorRepository = groupTutorRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.packRepository = packRepository;
        this.studentPackRepository = studentPackRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @Override
    public Optional<CenterGroupSummary> findGroupById(UUID groupId) {
        return groupRepository.findById(groupId).map(this::toGroupSummary);
    }

    @Override
    public List<CenterGroupSummary> findActiveGroupsByAcademicYear(UUID academicYearId) {
        List<CenterGroup> groups =
                groupRepository.findByAcademicYearIdAndStatusOrderByCodeAsc(academicYearId, CenterGroupStatus.ACTIVE);
        return groups.stream().map(this::toGroupSummary).toList();
    }

    @Override
    public List<CenterGroupEnrollmentSummary> findActiveEnrollmentsByStudent(UUID studentId) {
        List<CenterGroupEnrollment> enrollments =
                enrollmentRepository.findByStudentIdAndStatus(studentId, CenterGroupEnrollmentStatus.ACTIVE);
        return mapEnrollments(enrollments);
    }

    @Override
    public List<CenterGroupEnrollmentSummary> findActiveEnrollmentsByGroup(UUID groupId) {
        List<CenterGroupEnrollment> enrollments =
                enrollmentRepository.findByGroupIdAndStatus(groupId, CenterGroupEnrollmentStatus.ACTIVE);
        return mapEnrollments(enrollments);
    }

    @Override
    public Optional<CenterSessionPackSummary> findPackById(UUID packId) {
        return packRepository.findById(packId).map(this::toPackSummary);
    }

    @Override
    public Set<UUID> findAssignedGroupIdsByTeacherId(UUID teacherId) {
        return groupTutorRepository.findByTeacherIdAndActiveTrue(teacherId).stream()
                .map(CenterGroupTutor::getGroupId)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isTeacherAssignedToGroup(UUID teacherId, UUID groupId) {
        return groupTutorRepository.existsByGroupIdAndTeacherIdAndActiveTrue(groupId, teacherId);
    }

    @Override
    public StudentCenterSummary getStudentCenterSummary(UUID studentId, LocalDate fromDate, LocalDate toDate) {
        List<CenterGroupEnrollment> activeEnrollments =
                enrollmentRepository.findByStudentIdAndStatus(studentId, CenterGroupEnrollmentStatus.ACTIVE);
        List<CenterStudentPack> activePacks = studentPackRepository
                .findByStudentIdAndStatusOrderByStartDateAsc(studentId, CenterStudentPackStatus.ACTIVE);
        long remainingSessions = 0L;
        for (CenterStudentPack pack : activePacks) {
            Integer rem = pack.getRemainingSessions();
            if (rem != null) {
                remainingSessions = Math.addExact(remainingSessions, rem);
            }
        }

        List<CenterSessionAttendance> allAttendances = attendanceRepository.findByStudentId(studentId);
        List<CenterSessionAttendance> filteredAttendances = filterAttendancesBySessionDate(
                allAttendances, fromDate, toDate);
        CenterParticipationCalculator.ParticipationStats stats =
                CenterParticipationCalculator.computeParticipation(filteredAttendances);

        return new StudentCenterSummary(
                studentId,
                fromDate,
                toDate,
                activeEnrollments.size(),
                activePacks.size(),
                remainingSessions,
                stats.totalSessionsRecorded(),
                stats.presentCount(),
                stats.lateCount(),
                stats.totalLateMinutes(),
                stats.absentCount(),
                stats.excusedCount(),
                stats.attendanceRatePercent(),
                stats.averageParticipationRating(),
                stats.homeworkCompletionRatePercent());
    }

    @Override
    public TutorCenterWorkloadSummary getTutorWorkloadSummary(
            UUID teacherId, LocalDate fromDate, LocalDate toDate) {
        List<CenterGroupTutor> activeAssignments = groupTutorRepository.findByTeacherIdAndActiveTrue(teacherId);
        Set<UUID> activeGroupIds = activeAssignments.stream()
                .map(CenterGroupTutor::getGroupId)
                .collect(Collectors.toSet());

        Map<UUID, Long> hourlyRateByGroupId = new HashMap<>();
        for (CenterGroupTutor assignment : activeAssignments) {
            if (assignment.getHourlyRateXof() != null) {
                hourlyRateByGroupId.putIfAbsent(assignment.getGroupId(), assignment.getHourlyRateXof());
            }
        }

        List<CenterSession> sessions = (fromDate != null && toDate != null)
                ? sessionRepository.findByTeacherIdAndSessionDateBetween(teacherId, fromDate, toDate)
                : sessionRepository.findByTeacherId(teacherId).stream()
                        .filter(s -> (fromDate == null || !s.getSessionDate().isBefore(fromDate))
                                && (toDate == null || !s.getSessionDate().isAfter(toDate)))
                        .toList();

        long scheduledCount = 0L;
        long completedCount = 0L;
        long cancelledCount = 0L;
        long completedMinutes = 0L;
        List<CenterParticipationCalculator.CompletedSessionWork> completedWork = new ArrayList<>();

        for (CenterSession session : sessions) {
            switch (session.getStatus()) {
                case SCHEDULED, IN_PROGRESS -> scheduledCount++;
                case COMPLETED -> {
                    completedCount++;
                    completedMinutes = Math.addExact(completedMinutes, session.getDurationMinutes());
                    long rate = hourlyRateByGroupId.getOrDefault(session.getGroupId(), 0L);
                    completedWork.add(new CenterParticipationCalculator.CompletedSessionWork(
                            session.getDurationMinutes(), rate));
                }
                case CANCELLED -> cancelledCount++;
            }
        }

        long estimatedRemunerationXof =
                CenterParticipationCalculator.computeEstimatedRemunerationXof(completedWork);

        return new TutorCenterWorkloadSummary(
                teacherId,
                fromDate,
                toDate,
                activeGroupIds.size(),
                scheduledCount,
                completedCount,
                cancelledCount,
                completedMinutes,
                estimatedRemunerationXof);
    }

    private List<CenterSessionAttendance> filterAttendancesBySessionDate(
            List<CenterSessionAttendance> attendances, LocalDate fromDate, LocalDate toDate) {
        if (attendances.isEmpty()) {
            return List.of();
        }
        Set<UUID> sessionIds = attendances.stream()
                .map(CenterSessionAttendance::getSessionId)
                .collect(Collectors.toSet());
        Map<UUID, CenterSession> sessionsById = sessionRepository.findByIdIn(sessionIds).stream()
                .collect(Collectors.toMap(CenterSession::getId, Function.identity()));
        return attendances.stream()
                .filter(a -> {
                    CenterSession session = sessionsById.get(a.getSessionId());
                    if (session == null || session.getStatus() == CenterSessionStatus.CANCELLED) {
                        return false;
                    }
                    if (fromDate != null && session.getSessionDate().isBefore(fromDate)) {
                        return false;
                    }
                    return toDate == null || !session.getSessionDate().isAfter(toDate);
                })
                .toList();
    }

    private List<CenterGroupEnrollmentSummary> mapEnrollments(List<CenterGroupEnrollment> enrollments) {
        if (enrollments.isEmpty()) {
            return List.of();
        }
        Set<UUID> groupIds = enrollments.stream()
                .map(CenterGroupEnrollment::getGroupId)
                .collect(Collectors.toSet());
        Map<UUID, CenterGroup> groupsById = groupRepository.findByIdIn(groupIds).stream()
                .collect(Collectors.toMap(CenterGroup::getId, Function.identity()));
        return enrollments.stream()
                .map(e -> {
                    CenterGroup group = groupsById.get(e.getGroupId());
                    return new CenterGroupEnrollmentSummary(
                            e.getId(),
                            e.getGroupId(),
                            group != null ? group.getCode() : null,
                            group != null ? group.getName() : null,
                            e.getStudentId(),
                            e.getStudentPackId(),
                            e.getEnrolledAt(),
                            e.getEndedAt(),
                            e.getStatus().name());
                })
                .toList();
    }

    private CenterGroupSummary toGroupSummary(CenterGroup group) {
        long activeCount =
                enrollmentRepository.countByGroupIdAndStatus(group.getId(), CenterGroupEnrollmentStatus.ACTIVE);
        return new CenterGroupSummary(
                group.getId(),
                group.getAcademicYearId(),
                group.getGradeLevelId(),
                group.getSubjectId(),
                group.getDefaultPackId(),
                group.getCode(),
                group.getName(),
                group.getGroupType().name(),
                group.getRoomName(),
                group.getCapacity(),
                activeCount,
                group.getStartDate(),
                group.getEndDate(),
                group.getStatus().name(),
                group.isActive());
    }

    private CenterSessionPackSummary toPackSummary(CenterSessionPack pack) {
        return new CenterSessionPackSummary(
                pack.getId(),
                pack.getAcademicYearId(),
                pack.getGradeLevelId(),
                pack.getSubjectId(),
                pack.getCode(),
                pack.getName(),
                pack.getBillingMode().name(),
                pack.getIncludedSessions(),
                pack.getDurationDays(),
                pack.getPriceXof(),
                pack.isActive());
    }
}
