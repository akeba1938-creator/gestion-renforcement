package com.ecole.saas.communication.infrastructure;

import com.ecole.saas.communication.domain.DeliveryEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Dépôt strictement en ajout seul pour {@link DeliveryEvent} (aucune méthode de suppression).
 */
public interface DeliveryEventRepository extends Repository<DeliveryEvent, UUID> {

    DeliveryEvent save(DeliveryEvent event);

    List<DeliveryEvent> findByDeliveryIdOrderBySequenceNoAsc(UUID deliveryId);

    int countByDeliveryId(UUID deliveryId);
}
