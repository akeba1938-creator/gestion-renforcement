package com.ecole.saas.attendance.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.ClassRoomSummary;
import com.ecole.saas.attendance.AttendanceReportingOverview;
import com.ecole.saas.attendance.AttendanceSummaryPort;
import com.ecole.saas.attendance.ClassAttendanceReportingBreakdown;
import com.ecole.saas.attendance.StudentAttendanceSummary;
import com.ecole.saas.attendance.domain.AttendanceRecord;
import com.ecole.saas.attendance.domain.AttendanceSession;
import com.ecole.saas.attendance.domain.AttendanceSessionStatus;
import com.ecole.saas.attendance.domain.AttendanceSummaryCalculator;
import com.ecole.saas.attendance.infrastructure.AttendanceRecordRepository;
import com.ecole.saas.attendance.infrastructure.AttendanceSessionRepository;
import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation de la frontière publique {@link AttendanceSummaryPort} dans le contexte tenant courant.
 */
@Service
public class AttendanceSummaryQueryService implements AttendanceSummaryPort {

    private final AttendanceSessionService sessionService;
    private final AcademicStructurePort academicStructurePort;
    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository recordRepository;

    public AttendanceSummaryQueryService(
            AttendanceSessionService sessionService,
            AcademicStructurePort academicStructurePort,
            AttendanceSessionRepository sessionRepository,
            AttendanceRecordRepository recordRepository) {
        this.sessionService = sessionService;
        this.academicStructurePort = academicStructurePort;
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentAttendanceSummary getStudentPeriodSummary(UUID studentId, UUID evaluationPeriodId) {
        TenantContext.requireCurrent();
        return sessionService.computeStudentSummaryInternal(studentId, null, evaluationPeriodId, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentAttendanceSummary getStudentSummary(
            UUID studentId,
            UUID academicYearId,
            UUID evaluationPeriodId,
            LocalDate fromDate,
            LocalDate toDate) {
        TenantContext.requireCurrent();
        return sessionService.computeStudentSummaryInternal(
                studentId, academicYearId, evaluationPeriodId, fromDate, toDate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentAttendanceSummary> getClassPeriodSummaries(UUID classRoomId, UUID evaluationPeriodId) {
        TenantContext.requireCurrent();
        if (classRoomId == null) {
            return List.of();
        }
        ClassRoomSummary classRoom = academicStructurePort.findClassRoomById(classRoomId).orElse(null);
        if (classRoom == null) {
            return List.of();
        }
        return sessionService.computeClassSummariesInternal(classRoom, evaluationPeriodId, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceReportingOverview getAttendanceReportingOverview(
            UUID academicYearId,
            UUID evaluationPeriodId,
            Collection<UUID> classRoomIds,
            LocalDate fromDate,
            LocalDate toDate) {
        TenantContext.requireCurrent();
        Set<UUID> scopedClasses = classRoomIds == null
                ? null
                : classRoomIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (scopedClasses != null && scopedClasses.isEmpty()) {
            return emptyOverview(academicYearId, evaluationPeriodId, fromDate, toDate);
        }

        Specification<AttendanceSession> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("status"), AttendanceSessionStatus.CANCELLED));
            if (academicYearId != null) {
                predicates.add(cb.equal(root.get("academicYearId"), academicYearId));
            }
            if (evaluationPeriodId != null) {
                predicates.add(cb.equal(root.get("evaluationPeriodId"), evaluationPeriodId));
            }
            if (scopedClasses != null) {
                predicates.add(root.get("classRoomId").in(scopedClasses));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sessionDate"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("sessionDate"), toDate));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        List<AttendanceSession> sessions = sessionRepository.findAll(spec);
        if (sessions.isEmpty()) {
            return emptyOverview(academicYearId, evaluationPeriodId, fromDate, toDate);
        }

        List<UUID> sessionIds = sessions.stream().map(AttendanceSession::getId).toList();
        List<AttendanceRecord> allRecords = recordRepository.findBySessionIdIn(sessionIds);

        Map<UUID, UUID> classBySession = new HashMap<>(sessions.size());
        Map<UUID, Long> sessionCountsByClass = new LinkedHashMap<>();
        for (AttendanceSession session : sessions) {
            classBySession.put(session.getId(), session.getClassRoomId());
            sessionCountsByClass.merge(session.getClassRoomId(), 1L, Long::sum);
        }

        Map<UUID, List<AttendanceRecord>> recordsByClass = new LinkedHashMap<>();
        for (UUID classId : sessionCountsByClass.keySet()) {
            recordsByClass.put(classId, new ArrayList<>());
        }
        for (AttendanceRecord record : allRecords) {
            UUID classId = classBySession.get(record.getSessionId());
            if (classId != null) {
                recordsByClass.computeIfAbsent(classId, ignored -> new ArrayList<>()).add(record);
            }
        }

        AttendanceSummaryCalculator.AttendanceMetrics overall = AttendanceSummaryCalculator.compute(allRecords);
        List<ClassAttendanceReportingBreakdown> breakdowns = new ArrayList<>(sessionCountsByClass.size());
        for (Map.Entry<UUID, Long> entry : sessionCountsByClass.entrySet()) {
            UUID classId = entry.getKey();
            AttendanceSummaryCalculator.AttendanceMetrics metrics =
                    AttendanceSummaryCalculator.compute(recordsByClass.getOrDefault(classId, List.of()));
            breakdowns.add(new ClassAttendanceReportingBreakdown(
                    classId,
                    entry.getValue(),
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

        return new AttendanceReportingOverview(
                academicYearId,
                evaluationPeriodId,
                fromDate,
                toDate,
                sessions.size(),
                overall.totalSessions(),
                overall.presentCount(),
                overall.lateCount(),
                overall.totalLateMinutes(),
                overall.absentCount(),
                overall.justifiedAbsenceCount(),
                overall.unjustifiedAbsenceCount(),
                overall.excusedCount(),
                overall.attendanceRatePercent(),
                List.copyOf(breakdowns));
    }

    private static AttendanceReportingOverview emptyOverview(
            UUID academicYearId, UUID evaluationPeriodId, LocalDate fromDate, LocalDate toDate) {
        return new AttendanceReportingOverview(
                academicYearId,
                evaluationPeriodId,
                fromDate,
                toDate,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                null,
                List.of());
    }
}
