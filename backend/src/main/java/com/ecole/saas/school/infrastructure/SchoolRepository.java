package com.ecole.saas.school.infrastructure;

import com.ecole.saas.school.domain.School;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolRepository extends JpaRepository<School, UUID> {

    Optional<School> findByCode(String code);
}
