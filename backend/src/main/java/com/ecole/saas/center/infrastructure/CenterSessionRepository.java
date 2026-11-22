package com.ecole.saas.center.infrastructure;

import com.ecole.saas.center.domain.CenterSession;
import com.ecole.saas.center.domain.CenterSessionStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CenterSessionRepository
        extends JpaRepository<CenterSession, UUID>, JpaSpecificationExecutor<CenterSession> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CenterSession s WHERE s.id = :id")
    Optional<CenterSession> findByIdForUpdate(@Param("id") UUID id);

    List<CenterSession> findByGroupIdAndSessionDateAndStatusNot(
            UUID groupId, LocalDate sessionDate, CenterSessionStatus excludedStatus);

    List<CenterSession> findByTeacherIdAndSessionDateAndStatusNot(
            UUID teacherId, LocalDate sessionDate, CenterSessionStatus excludedStatus);

    List<CenterSession> findByGroupIdAndSessionDateBetweenAndStatusNot(
            UUID groupId, LocalDate fromDate, LocalDate toDate, CenterSessionStatus excludedStatus);

    List<CenterSession> findByTeacherIdInAndSessionDateBetweenAndStatusNot(
            Collection<UUID> teacherIds,
            LocalDate fromDate,
            LocalDate toDate,
            CenterSessionStatus excludedStatus);

    List<CenterSession> findByTeacherIdAndSessionDateBetween(
            UUID teacherId, LocalDate fromDate, LocalDate toDate);

    List<CenterSession> findByTeacherId(UUID teacherId);

    List<CenterSession> findByIdIn(Collection<UUID> ids);
}
