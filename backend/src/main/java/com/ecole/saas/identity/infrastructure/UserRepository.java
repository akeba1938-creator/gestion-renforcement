package com.ecole.saas.identity.infrastructure;

import com.ecole.saas.identity.domain.User;
import com.ecole.saas.identity.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    List<User> findByStatusOrderByLastNameAscFirstNameAsc(UserStatus status);
}
