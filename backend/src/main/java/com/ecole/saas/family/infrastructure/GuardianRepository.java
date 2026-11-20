package com.ecole.saas.family.infrastructure;

import com.ecole.saas.family.domain.Guardian;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GuardianRepository extends JpaRepository<Guardian, UUID>, JpaSpecificationExecutor<Guardian> {

    Page<Guardian> findByFamilyId(UUID familyId, Pageable pageable);

    List<Guardian> findByFamilyId(UUID familyId);
}
