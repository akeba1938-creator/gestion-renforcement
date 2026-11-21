package com.ecole.saas.identity.infrastructure;

import com.ecole.saas.identity.domain.UserRole;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByUserId(UUID userId);

    List<UserRole> findByUserIdIn(Collection<UUID> userIds);
}
