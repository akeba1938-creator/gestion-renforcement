package com.ecole.saas.identity.infrastructure;

import com.ecole.saas.identity.domain.Role;
import com.ecole.saas.identity.domain.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, RoleCode> {
}
