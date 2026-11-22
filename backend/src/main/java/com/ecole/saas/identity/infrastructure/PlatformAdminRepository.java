package com.ecole.saas.identity.infrastructure;

import com.ecole.saas.identity.domain.PlatformAdmin;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, UUID> {

    Optional<PlatformAdmin> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PlatformAdmin a WHERE a.email = :email")
    Optional<PlatformAdmin> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PlatformAdmin a WHERE a.id = :id")
    Optional<PlatformAdmin> findByIdForUpdate(@Param("id") UUID id);
}
