package com.ecole.saas.subscription.infrastructure;

import com.ecole.saas.subscription.domain.SubscriptionEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, UUID> {

    Page<SubscriptionEvent> findBySubscriptionIdOrderBySequenceNoDesc(UUID subscriptionId, Pageable pageable);

    @Query("SELECT COALESCE(MAX(e.sequenceNo), 0) FROM SubscriptionEvent e WHERE e.subscriptionId = :subscriptionId")
    int findMaxSequenceNoBySubscriptionId(@Param("subscriptionId") UUID subscriptionId);
}
