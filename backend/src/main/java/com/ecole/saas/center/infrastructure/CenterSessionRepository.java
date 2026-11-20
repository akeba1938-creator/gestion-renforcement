package com.ecole.saas.center.infrastructure;

import com.ecole.saas.center.domain.CenterSession;
import com.ecole.saas.center.domain.CenterSessionStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CenterSessionRepository
        extends JpaRepository<CenterSession, UUID>, JpaSpecificationExecutor<CenterSession> {

    List<CenterSession> findByGroupIdAndSessionDateAndStatusNot(
            UUID groupId, LocalDate sessionDate, CenterSessionStatus excludedStatus);

    List<CenterSession> findByTeacherIdAndSessionDateAndStatusNot(
            UUID teacherId, LocalDate sessionDate, CenterSessionStatus excludedStatus);

    List<CenterSession> findByGroupIdAndSessionDateBetweenAndStatusNot(
            UUID groupId, LocalDate fromDate, LocalDate toDate, CenterSessionStatus excludedStatus);

    List<CenterSession> findByTeacherIdAndSessionDateBetween(
            UUID teacherId, LocalDate fromDate, LocalDate toDate);

    List<CenterSession> findByTeacherId(UUID teacherId);

    List<CenterSession> findByIdIn(Collection<UUID> ids);
}
