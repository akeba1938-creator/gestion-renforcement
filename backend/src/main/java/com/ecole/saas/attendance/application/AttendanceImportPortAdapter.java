package com.ecole.saas.attendance.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.EvaluationPeriodSummary;
import com.ecole.saas.attendance.AttendanceImportPort;
import com.ecole.saas.attendance.AttendanceSessionImportContext;
import com.ecole.saas.attendance.ImportAttendanceEntryCommand;
import com.ecole.saas.attendance.domain.AttendanceRecord;
import com.ecole.saas.attendance.domain.AttendanceRecordStatus;
import com.ecole.saas.attendance.infrastructure.AttendanceRecordRepository;
import com.ecole.saas.attendance.infrastructure.AttendanceSessionRepository;
import com.ecole.saas.shared.domain.TenantContext;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptateur applicatif de {@link AttendanceImportPort} déléguant à {@link AttendanceSessionService}
 * afin de préserver les contrôles RBAC, restriction enseignant, verrous de période et {@code saveAll}.
 */
@Service
public class AttendanceImportPortAdapter implements AttendanceImportPort {

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository recordRepository;
    private final AcademicStructurePort academicStructurePort;
    private final AttendanceSessionService attendanceSessionService;

    public AttendanceImportPortAdapter(
            AttendanceSessionRepository sessionRepository,
            AttendanceRecordRepository recordRepository,
            AcademicStructurePort academicStructurePort,
            AttendanceSessionService attendanceSessionService) {
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.academicStructurePort = academicStructurePort;
        this.attendanceSessionService = attendanceSessionService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttendanceSessionImportContext> findSessionImportContext(UUID sessionId) {
        TenantContext.requireCurrent();
        if (sessionId == null) {
            return Optional.empty();
        }
        return sessionRepository.findById(sessionId).map(session -> {
            String periodStatus = session.getEvaluationPeriodId() != null
                    ? academicStructurePort
                            .findEvaluationPeriodById(session.getEvaluationPeriodId())
                            .map(EvaluationPeriodSummary::status)
                            .orElse("UNKNOWN")
                    : null;
            Set<UUID> existingStudents = recordRepository
                    .findBySessionIdOrderByCreatedAtAsc(sessionId)
                    .stream()
                    .map(AttendanceRecord::getStudentId)
                    .collect(Collectors.toUnmodifiableSet());
            return new AttendanceSessionImportContext(
                    session.getId(),
                    session.getAcademicYearId(),
                    session.getEvaluationPeriodId(),
                    periodStatus,
                    session.getClassRoomId(),
                    session.getSubjectId(),
                    session.getSessionDate(),
                    session.getStatus().name(),
                    existingStudents);
        });
    }

    @Override
    public int upsertAttendanceForImport(
            UUID sessionId, List<ImportAttendanceEntryCommand> entries, UUID actorId) {
        List<AttendanceSessionService.AttendanceRecordInput> inputs = entries.stream()
                .map(entry -> new AttendanceSessionService.AttendanceRecordInput(
                        entry.studentId(),
                        AttendanceRecordStatus.valueOf(entry.status().trim().toUpperCase(Locale.ROOT)),
                        entry.lateMinutes(),
                        entry.notes()))
                .toList();
        return attendanceSessionService.upsertRecords(sessionId, inputs, actorId).records().size();
    }
}
