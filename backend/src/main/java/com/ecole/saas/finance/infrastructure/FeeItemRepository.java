package com.ecole.saas.finance.infrastructure;

import com.ecole.saas.finance.domain.FeeItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FeeItemRepository extends JpaRepository<FeeItem, UUID>, JpaSpecificationExecutor<FeeItem> {

    boolean existsByAcademicYearIdAndCode(UUID academicYearId, String code);

    Optional<FeeItem> findByAcademicYearIdAndCode(UUID academicYearId, String code);

    List<FeeItem> findByIdIn(Collection<UUID> ids);
}
