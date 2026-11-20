package com.ecole.saas.attendance.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.ClassRoomSummary;
import com.ecole.saas.attendance.AttendanceSummaryPort;
import com.ecole.saas.attendance.StudentAttendanceSummary;
import com.ecole.saas.shared.domain.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation de la frontière publique {@link AttendanceSummaryPort} dans le contexte tenant courant.
 */
@Service
public class AttendanceSummaryQueryService implements AttendanceSummaryPort {

    private final AttendanceSessionService sessionService;
    private final AcademicStructurePort academicStructurePort;

    public AttendanceSummaryQueryService(
            AttendanceSessionService sessionService,
            AcademicStructurePort academicStructurePort) {
        this.sessionService = sessionService;
        this.academicStructurePort = academicStructurePort;
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
}
