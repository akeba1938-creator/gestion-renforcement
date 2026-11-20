package com.ecole.saas.academic.infrastructure;

import com.ecole.saas.academic.domain.EvaluationPeriod;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationPeriodRepository extends JpaRepository<EvaluationPeriod, UUID> {

    List<EvaluationPeriod> findByAcademicYearIdOrderBySequenceOrderAsc(UUID academicYearId);

    Page<EvaluationPeriod> findByAcademicYearIdOrderBySequenceOrderAsc(UUID academicYearId, Pageable pageable);

    boolean existsByAcademicYearIdAndCode(UUID academicYearId, String code);

    boolean existsByAcademicYearIdAndSequenceOrder(UUID academicYearId, int sequenceOrder);

    boolean existsByAcademicYearIdAndSequenceOrderAndIdNot(UUID academicYearId, int sequenceOrder, UUID id);

    long countByAcademicYearId(UUID academicYearId);
}
