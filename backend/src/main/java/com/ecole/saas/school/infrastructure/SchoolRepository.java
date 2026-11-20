package com.ecole.saas.school.infrastructure;

import com.ecole.saas.school.domain.School;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SchoolRepository extends JpaRepository<School, UUID>, JpaSpecificationExecutor<School> {

    Optional<School> findByCode(String code);

    boolean existsByCode(String code);
}
