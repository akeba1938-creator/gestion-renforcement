package com.ecole.saas.grade.infrastructure;

import com.ecole.saas.grade.domain.PeriodReportCard;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PeriodReportCardRepository
        extends JpaRepository<PeriodReportCard, UUID>, JpaSpecificationExecutor<PeriodReportCard> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PeriodReportCard c WHERE c.id = :id")
    Optional<PeriodReportCard> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM PeriodReportCard c
            WHERE c.evaluationPeriodId = :evaluationPeriodId
              AND c.classRoomId = :classRoomId
            ORDER BY c.id ASC
            """)
    List<PeriodReportCard> findByEvaluationPeriodIdAndClassRoomIdForUpdate(
            @Param("evaluationPeriodId") UUID evaluationPeriodId,
            @Param("classRoomId") UUID classRoomId);

    Optional<PeriodReportCard> findByEvaluationPeriodIdAndStudentId(UUID evaluationPeriodId, UUID studentId);

    List<PeriodReportCard> findByEvaluationPeriodIdAndStudentIdIn(
            UUID evaluationPeriodId, Collection<UUID> studentIds);

    List<PeriodReportCard> findByEvaluationPeriodIdAndClassRoomIdOrderByRankInClassAscStudentIdAsc(
            UUID evaluationPeriodId, UUID classRoomId);

    List<PeriodReportCard> findByStudentIdAndAcademicYearIdOrderByComputedAtAsc(UUID studentId, UUID academicYearId);

    List<PeriodReportCard> findByAcademicYearId(UUID academicYearId);

    List<PeriodReportCard> findByAcademicYearIdAndClassRoomIdIn(UUID academicYearId, Collection<UUID> classRoomIds);

    List<PeriodReportCard> findByEvaluationPeriodId(UUID evaluationPeriodId);

    List<PeriodReportCard> findByEvaluationPeriodIdAndClassRoomIdIn(
            UUID evaluationPeriodId, Collection<UUID> classRoomIds);
}
