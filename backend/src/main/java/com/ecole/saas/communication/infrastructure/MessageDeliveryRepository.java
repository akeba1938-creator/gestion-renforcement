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

public interface MessageDeliveryRepository
        extends JpaRepository<MessageDelivery, UUID>, JpaSpecificationExecutor<MessageDelivery> {

    List<MessageDelivery> findByCampaignIdOrderByCreatedAtAsc(UUID campaignId);

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
}
