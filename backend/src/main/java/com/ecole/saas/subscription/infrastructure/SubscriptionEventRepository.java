package com.ecole.saas.subscription.infrastructure;

import com.ecole.saas.subscription.domain.SubscriptionEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Dépôt strictement en ajout seul sur {@code subscription_events} : aucune méthode de suppression n'est exposée.
 */
public interface SubscriptionEventRepository extends Repository<SubscriptionEvent, UUID> {

    SubscriptionEvent save(SubscriptionEvent event);

    Page<SubscriptionEvent> findBySubscriptionIdOrderBySequenceNoDesc(UUID subscriptionId, Pageable pageable);

    @Query("SELECT COALESCE(MAX(e.sequenceNo), 0) FROM SubscriptionEvent e WHERE e.subscriptionId = :subscriptionId")
    int findMaxSequenceNoBySubscriptionId(@Param("subscriptionId") UUID subscriptionId);
}
