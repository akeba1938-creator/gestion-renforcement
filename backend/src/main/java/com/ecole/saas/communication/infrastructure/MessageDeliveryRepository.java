package com.ecole.saas.communication.infrastructure;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.DeliveryStatus;
import com.ecole.saas.communication.MessageCategory;
import com.ecole.saas.communication.domain.MessageDelivery;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageDeliveryRepository
        extends JpaRepository<MessageDelivery, UUID>, JpaSpecificationExecutor<MessageDelivery> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM MessageDelivery d WHERE d.id = :id")
    Optional<MessageDelivery> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM MessageDelivery d WHERE d.id IN :ids ORDER BY d.id ASC")
    List<MessageDelivery> findByIdInForUpdate(@Param("ids") Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d FROM MessageDelivery d
            WHERE d.campaignId = :campaignId
              AND d.status = :status
            ORDER BY d.id ASC
            """)
    List<MessageDelivery> findByCampaignIdAndStatusForUpdate(
            @Param("campaignId") UUID campaignId,
            @Param("status") DeliveryStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d FROM MessageDelivery d
            WHERE d.recipientUserId = :recipientUserId
              AND d.channel = :channel
              AND d.status IN :statuses
            ORDER BY d.id ASC
            """)
    List<MessageDelivery> findByRecipientUserIdAndChannelAndStatusInForUpdate(
            @Param("recipientUserId") UUID recipientUserId,
            @Param("channel") CommunicationChannel channel,
            @Param("statuses") Collection<DeliveryStatus> statuses);

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
