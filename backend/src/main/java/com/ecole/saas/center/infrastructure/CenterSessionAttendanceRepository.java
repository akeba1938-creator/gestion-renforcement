package com.ecole.saas.center.infrastructure;

import com.ecole.saas.center.domain.CenterSessionAttendance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CenterSessionAttendanceRepository extends JpaRepository<CenterSessionAttendance, UUID> {

    List<CenterSessionAttendance> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Optional<CenterSessionAttendance> findBySessionIdAndStudentId(UUID sessionId, UUID studentId);

    List<CenterSessionAttendance> findByStudentId(UUID studentId);
}
