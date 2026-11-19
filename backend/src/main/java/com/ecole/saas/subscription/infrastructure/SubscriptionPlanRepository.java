package com.ecole.saas.subscription.infrastructure;

import com.ecole.saas.subscription.domain.SubscriptionPlan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    Optional<SubscriptionPlan> findByCode(String code);

    boolean existsByCode(String code);

    Page<SubscriptionPlan> findByActive(boolean active, Pageable pageable);

    Optional<SubscriptionPlan> findByIdAndActiveTrue(UUID id);
}
