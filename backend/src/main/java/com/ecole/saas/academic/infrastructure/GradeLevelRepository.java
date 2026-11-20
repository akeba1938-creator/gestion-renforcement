package com.ecole.saas.academic.infrastructure;

import com.ecole.saas.academic.domain.GradeLevel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GradeLevelRepository
        extends JpaRepository<GradeLevel, UUID>, JpaSpecificationExecutor<GradeLevel> {

    Optional<GradeLevel> findByCode(String code);

    boolean existsByCode(String code);
}
