package com.ecole.saas.grade.infrastructure;

import com.ecole.saas.grade.domain.StudentGrade;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentGradeRepository extends JpaRepository<StudentGrade, UUID> {

    List<StudentGrade> findByEvaluationIdOrderByCreatedAtAsc(UUID evaluationId);

    List<StudentGrade> findByEvaluationIdIn(Collection<UUID> evaluationIds);

    Optional<StudentGrade> findByEvaluationIdAndStudentId(UUID evaluationId, UUID studentId);
}
