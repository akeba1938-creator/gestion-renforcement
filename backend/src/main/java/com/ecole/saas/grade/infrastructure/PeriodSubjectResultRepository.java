package com.ecole.saas.grade.infrastructure;

import com.ecole.saas.grade.domain.PeriodSubjectResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodSubjectResultRepository extends JpaRepository<PeriodSubjectResult, UUID> {

    List<PeriodSubjectResult> findByReportCardIdOrderBySubjectNameAsc(UUID reportCardId);

    List<PeriodSubjectResult> findByReportCardIdIn(Collection<UUID> reportCardIds);

    Optional<PeriodSubjectResult> findByReportCardIdAndSubjectId(UUID reportCardId, UUID subjectId);
}
