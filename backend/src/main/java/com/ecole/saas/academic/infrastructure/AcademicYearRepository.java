package com.ecole.saas.academic.infrastructure;

import com.ecole.saas.academic.domain.AcademicYear;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, UUID> {

    Optional<AcademicYear> findByCode(String code);

    Optional<AcademicYear> findByCurrentTrue();
}
