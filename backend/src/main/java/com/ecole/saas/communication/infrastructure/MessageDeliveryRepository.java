package com.ecole.saas.communication.infrastructure;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.DeliveryStatus;
import com.ecole.saas.communication.MessageCategory;
import com.ecole.saas.communication.domain.MessageDelivery;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface MessageDeliveryRepository
        extends JpaRepository<MessageDelivery, UUID>, JpaSpecificationExecutor<MessageDelivery> {

    interface DeliveryStatusCount {
        DeliveryStatus getStatus();

        long getDeliveryCount();
    }

    interface DeliveryChannelCount {
        CommunicationChannel getChannel();

        long getDeliveryCount();
    }

    List<MessageDelivery> findByCampaignIdOrderByCreatedAtAsc(UUID campaignId);

    List<MessageDelivery> findByCampaignIdAndStatusOrderByCreatedAtAsc(UUID campaignId, DeliveryStatus status);

    Page<MessageDelivery> findByCampaignId(UUID campaignId, Pageable pageable);

    Page<MessageDelivery> findByCampaignIdAndStatus(UUID campaignId, DeliveryStatus status, Pageable pageable);

    Optional<MessageDelivery> findByIdAndRecipientUserIdAndChannel(
            UUID id, UUID recipientUserId, CommunicationChannel channel);

    Page<MessageDelivery> findByRecipientUserIdAndChannel(
            UUID recipientUserId, CommunicationChannel channel, Pageable pageable);

    Page<MessageDelivery> findByRecipientUserIdAndChannelAndStatusIn(
            UUID recipientUserId,
            CommunicationChannel channel,
            Collection<DeliveryStatus> statuses,
            Pageable pageable);

    Page<MessageDelivery> findByRecipientUserIdAndChannelAndCategory(
            UUID recipientUserId,
            CommunicationChannel channel,
            MessageCategory category,
            Pageable pageable);

    Page<MessageDelivery> findByRecipientUserIdAndChannelAndCategoryAndStatusIn(
            UUID recipientUserId,
            CommunicationChannel channel,
            MessageCategory category,
            Collection<DeliveryStatus> statuses,
            Pageable pageable);

    List<MessageDelivery> findByRecipientUserIdAndChannelAndStatusIn(
            UUID recipientUserId,
            CommunicationChannel channel,
            Collection<DeliveryStatus> statuses);

    long countByRecipientUserIdAndChannelAndStatusIn(
            UUID recipientUserId,
            CommunicationChannel channel,
            Collection<DeliveryStatus> statuses);

    long countByStatus(DeliveryStatus status);

    long countByChannel(CommunicationChannel channel);

    @Query("SELECT d.status AS status, COUNT(d.id) AS deliveryCount FROM MessageDelivery d GROUP BY d.status")
    List<DeliveryStatusCount> countGroupedByStatus();

    @Query("SELECT d.channel AS channel, COUNT(d.id) AS deliveryCount FROM MessageDelivery d GROUP BY d.channel")
    List<DeliveryChannelCount> countGroupedByChannel();
}
