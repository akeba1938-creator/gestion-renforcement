package com.ecole.saas.subscription.infrastructure;

import com.ecole.saas.subscription.domain.Subscription;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
}
