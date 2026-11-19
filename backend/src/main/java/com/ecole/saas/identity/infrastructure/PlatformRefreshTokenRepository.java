package com.ecole.saas.identity.infrastructure;

import com.ecole.saas.identity.domain.PlatformRefreshToken;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformRefreshTokenRepository extends JpaRepository<PlatformRefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PlatformRefreshToken t where t.tokenHash = :tokenHash")
    Optional<PlatformRefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<PlatformRefreshToken> findByFamilyId(UUID familyId);
}
