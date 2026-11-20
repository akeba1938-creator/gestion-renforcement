package com.ecole.saas.academic.infrastructure;

import com.ecole.saas.academic.domain.Subject;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SubjectRepository extends JpaRepository<Subject, UUID>, JpaSpecificationExecutor<Subject> {

    Optional<Subject> findByCode(String code);

    boolean existsByCode(String code);
}
