package com.ecole.saas.attendance.infrastructure;

import com.ecole.saas.attendance.domain.AttendanceRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    List<AttendanceRecord> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<AttendanceRecord> findBySessionIdIn(Collection<UUID> sessionIds);

    List<AttendanceRecord> findByStudentIdAndSessionIdIn(UUID studentId, Collection<UUID> sessionIds);

    Optional<AttendanceRecord> findBySessionIdAndStudentId(UUID sessionId, UUID studentId);
}
