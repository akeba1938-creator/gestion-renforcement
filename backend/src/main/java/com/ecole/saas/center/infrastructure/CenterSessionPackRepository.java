package com.ecole.saas.center.infrastructure;

import com.ecole.saas.center.domain.CenterSessionPack;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CenterSessionPackRepository
        extends JpaRepository<CenterSessionPack, UUID>, JpaSpecificationExecutor<CenterSessionPack> {

    boolean existsByCode(String code);

    Optional<CenterSessionPack> findByCode(String code);
}
