package com.ecole.saas.family.infrastructure;

import com.ecole.saas.family.domain.Family;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FamilyRepository extends JpaRepository<Family, UUID>, JpaSpecificationExecutor<Family> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    List<Family> findByCodeIn(Collection<String> codes);
}
