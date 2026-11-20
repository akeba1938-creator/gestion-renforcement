package com.ecole.saas.academic.infrastructure;

import com.ecole.saas.academic.domain.AcademicYear;
import com.ecole.saas.academic.domain.AcademicYearStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AcademicYearRepository
        extends JpaRepository<AcademicYear, UUID>, JpaSpecificationExecutor<AcademicYear> {

    Optional<AcademicYear> findByCode(String code);

    boolean existsByCode(String code);

    Optional<AcademicYear> findByCurrentTrue();

    Page<AcademicYear> findByStatus(AcademicYearStatus status, Pageable pageable);
}
