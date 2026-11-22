package com.ecole.saas.attendance.infrastructure;

import com.ecole.saas.attendance.domain.AttendanceRecord;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    List<AttendanceRecord> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<AttendanceRecord> findBySessionIdIn(Collection<UUID> sessionIds);

    List<AttendanceRecord> findByStudentIdAndSessionIdIn(UUID studentId, Collection<UUID> sessionIds);

    Optional<AttendanceRecord> findBySessionIdAndStudentId(UUID sessionId, UUID studentId);

    @Query("""
            SELECT r
            FROM AttendanceRecord r
            JOIN AttendanceSession s ON s.id = r.sessionId
            WHERE r.studentId = :studentId
              AND s.status <> com.ecole.saas.attendance.domain.AttendanceSessionStatus.CANCELLED
              AND (:academicYearId IS NULL OR s.academicYearId = :academicYearId)
              AND (:evaluationPeriodId IS NULL OR s.evaluationPeriodId = :evaluationPeriodId)
              AND (:fromDate IS NULL OR s.sessionDate >= :fromDate)
              AND (:toDate IS NULL OR s.sessionDate <= :toDate)
            """)
    List<AttendanceRecord> findNonCancelledByStudentAndFilters(
            @Param("studentId") UUID studentId,
            @Param("academicYearId") UUID academicYearId,
            @Param("evaluationPeriodId") UUID evaluationPeriodId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
