package com.ecole.saas.schooldocs.infrastructure;

import com.ecole.saas.schooldocs.domain.DocumentTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentTemplateRepository
        extends JpaRepository<DocumentTemplate, UUID>, JpaSpecificationExecutor<DocumentTemplate> {

    boolean existsByCode(String code);

    Optional<DocumentTemplate> findByCode(String code);

    @Query("SELECT COUNT(t) FROM DocumentTemplate t WHERE t.active = :active")
    long countByActive(@Param("active") boolean active);
}
