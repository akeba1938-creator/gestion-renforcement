package com.ecole.saas.grade.infrastructure;

import com.ecole.saas.grade.domain.Evaluation;
import com.ecole.saas.grade.domain.EvaluationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EvaluationRepository
        extends JpaRepository<Evaluation, UUID>, JpaSpecificationExecutor<Evaluation> {

    List<Evaluation> findByEvaluationPeriodIdAndClassRoomIdAndStatusNotOrderByEvaluationDateAscCreatedAtAsc(
            UUID evaluationPeriodId, UUID classRoomId, EvaluationStatus excludedStatus);
}
