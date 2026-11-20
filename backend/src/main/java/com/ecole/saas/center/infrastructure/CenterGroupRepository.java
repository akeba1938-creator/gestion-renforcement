package com.ecole.saas.center.infrastructure;

import com.ecole.saas.center.domain.CenterGroup;
import com.ecole.saas.center.domain.CenterGroupStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CenterGroupRepository
        extends JpaRepository<CenterGroup, UUID>, JpaSpecificationExecutor<CenterGroup> {

    boolean existsByAcademicYearIdAndCode(UUID academicYearId, String code);

    List<CenterGroup> findByAcademicYearIdAndStatusOrderByCodeAsc(UUID academicYearId, CenterGroupStatus status);

    List<CenterGroup> findByIdIn(Collection<UUID> ids);
}
