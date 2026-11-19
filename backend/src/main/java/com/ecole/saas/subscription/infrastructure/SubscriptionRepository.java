package com.ecole.saas.subscription.infrastructure;

import com.ecole.saas.subscription.domain.Subscription;
import com.ecole.saas.subscription.domain.SubscriptionStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    boolean existsByStatusIn(Collection<SubscriptionStatus> statuses);

    Optional<Subscription> findFirstByStatusInOrderByCreatedAtDesc(Collection<SubscriptionStatus> statuses);

    Optional<Subscription> findFirstByOrderByCreatedAtDesc();
}
