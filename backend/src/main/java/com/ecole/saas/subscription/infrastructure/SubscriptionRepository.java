package com.ecole.saas.subscription.infrastructure;

import com.ecole.saas.subscription.domain.Subscription;
import com.ecole.saas.subscription.domain.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.id = :id")
    Optional<Subscription> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByStatusIn(Collection<SubscriptionStatus> statuses);

    Optional<Subscription> findFirstByStatusInOrderByCreatedAtDesc(Collection<SubscriptionStatus> statuses);

    Optional<Subscription> findFirstByOrderByCreatedAtDesc();
}
