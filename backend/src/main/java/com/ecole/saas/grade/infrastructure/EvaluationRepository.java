package com.ecole.saas.grade.infrastructure;

import com.ecole.saas.grade.domain.Evaluation;
import com.ecole.saas.grade.domain.EvaluationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvaluationRepository
        extends JpaRepository<Evaluation, UUID>, JpaSpecificationExecutor<Evaluation> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Evaluation e WHERE e.id = :id")
    Optional<Evaluation> findByIdForUpdate(@Param("id") UUID id);

    List<Evaluation> findByEvaluationPeriodIdAndClassRoomIdAndStatusNotOrderByEvaluationDateAscCreatedAtAsc(
            UUID evaluationPeriodId, UUID classRoomId, EvaluationStatus excludedStatus);
}
