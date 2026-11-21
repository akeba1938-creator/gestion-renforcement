package com.ecole.saas.communication.infrastructure;

import com.ecole.saas.communication.domain.DeliveryEvent;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Dépôt strictement en ajout seul pour {@link DeliveryEvent} (aucune méthode de suppression).
 */
public interface DeliveryEventRepository extends Repository<DeliveryEvent, UUID> {

    interface DeliveryMaxSequence {
        UUID getDeliveryId();

        int getMaxSequenceNo();
    }

    DeliveryEvent save(DeliveryEvent event);

    <S extends DeliveryEvent> List<S> saveAll(Iterable<S> events);

    List<DeliveryEvent> findByDeliveryIdOrderBySequenceNoAsc(UUID deliveryId);

    int countByDeliveryId(UUID deliveryId);

    @Query("SELECT COALESCE(MAX(e.sequenceNo), 0) FROM DeliveryEvent e WHERE e.deliveryId = :deliveryId")
    int findMaxSequenceNoByDeliveryId(@Param("deliveryId") UUID deliveryId);

    @Query("""
            SELECT e.deliveryId AS deliveryId, MAX(e.sequenceNo) AS maxSequenceNo
            FROM DeliveryEvent e
            WHERE e.deliveryId IN :deliveryIds
            GROUP BY e.deliveryId
            """)
    List<DeliveryMaxSequence> findMaxSequenceNoByDeliveryIdIn(@Param("deliveryIds") Collection<UUID> deliveryIds);
}
