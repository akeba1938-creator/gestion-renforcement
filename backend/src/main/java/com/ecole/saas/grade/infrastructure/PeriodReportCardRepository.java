package com.ecole.saas.grade.infrastructure;

import com.ecole.saas.grade.domain.PeriodReportCard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PeriodReportCardRepository
        extends JpaRepository<PeriodReportCard, UUID>, JpaSpecificationExecutor<PeriodReportCard> {

    Optional<PeriodReportCard> findByEvaluationPeriodIdAndStudentId(UUID evaluationPeriodId, UUID studentId);

    List<PeriodReportCard> findByEvaluationPeriodIdAndClassRoomIdOrderByRankInClassAscStudentIdAsc(
            UUID evaluationPeriodId, UUID classRoomId);

    List<PeriodReportCard> findByStudentIdAndAcademicYearIdOrderByComputedAtAsc(UUID studentId, UUID academicYearId);
}
