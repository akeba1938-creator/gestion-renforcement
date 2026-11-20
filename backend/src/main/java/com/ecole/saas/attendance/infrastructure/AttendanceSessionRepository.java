package com.ecole.saas.attendance.infrastructure;

import com.ecole.saas.attendance.domain.AttendanceSession;
import com.ecole.saas.attendance.domain.AttendanceSessionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AttendanceSessionRepository
        extends JpaRepository<AttendanceSession, UUID>, JpaSpecificationExecutor<AttendanceSession> {

    List<AttendanceSession> findByClassRoomIdAndEvaluationPeriodIdAndStatusNot(
            UUID classRoomId, UUID evaluationPeriodId, AttendanceSessionStatus excludedStatus);

    List<AttendanceSession> findByClassRoomIdAndSessionDateBetweenAndStatusNot(
            UUID classRoomId, LocalDate fromDate, LocalDate toDate, AttendanceSessionStatus excludedStatus);

    List<AttendanceSession> findByEvaluationPeriodIdAndStatusNot(
            UUID evaluationPeriodId, AttendanceSessionStatus excludedStatus);

    List<AttendanceSession> findByAcademicYearIdAndStatusNot(
            UUID academicYearId, AttendanceSessionStatus excludedStatus);
}
