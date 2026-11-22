package com.ecole.saas.attendance.infrastructure;

import com.ecole.saas.attendance.domain.AttendanceSession;
import com.ecole.saas.attendance.domain.AttendanceSessionStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceSessionRepository
        extends JpaRepository<AttendanceSession, UUID>, JpaSpecificationExecutor<AttendanceSession> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AttendanceSession s WHERE s.id = :id")
    Optional<AttendanceSession> findByIdForUpdate(@Param("id") UUID id);

    List<AttendanceSession> findByClassRoomIdAndEvaluationPeriodIdAndStatusNot(
            UUID classRoomId, UUID evaluationPeriodId, AttendanceSessionStatus excludedStatus);

    List<AttendanceSession> findByClassRoomIdAndSessionDateBetweenAndStatusNot(
            UUID classRoomId, LocalDate fromDate, LocalDate toDate, AttendanceSessionStatus excludedStatus);

    List<AttendanceSession> findByEvaluationPeriodIdAndStatusNot(
            UUID evaluationPeriodId, AttendanceSessionStatus excludedStatus);

    List<AttendanceSession> findByAcademicYearIdAndStatusNot(
            UUID academicYearId, AttendanceSessionStatus excludedStatus);
}
