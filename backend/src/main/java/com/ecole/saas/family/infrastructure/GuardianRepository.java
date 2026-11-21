package com.ecole.saas.family.infrastructure;

import com.ecole.saas.family.domain.Guardian;
import com.ecole.saas.family.domain.GuardianStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GuardianRepository extends JpaRepository<Guardian, UUID>, JpaSpecificationExecutor<Guardian> {

    Page<Guardian> findByFamilyId(UUID familyId, Pageable pageable);

    Page<Guardian> findByFamilyIdAndIdIn(UUID familyId, Collection<UUID> ids, Pageable pageable);

    boolean existsByFamilyIdAndIdIn(UUID familyId, Collection<UUID> ids);

    List<Guardian> findByFamilyId(UUID familyId);

    List<Guardian> findByFamilyIdAndStatusOrderByLastNameAscFirstNameAsc(UUID familyId, GuardianStatus status);

    List<Guardian> findByStatusOrderByLastNameAscFirstNameAsc(GuardianStatus status);

    List<Guardian> findByPhoneIn(Collection<String> phones);

    Optional<Guardian> findByUserId(UUID userId);
}
