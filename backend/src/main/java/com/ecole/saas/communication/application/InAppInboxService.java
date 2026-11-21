package com.ecole.saas.communication.application;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.DeliveryStatus;
import com.ecole.saas.communication.MessageCategory;
import com.ecole.saas.communication.domain.CommunicationNotFoundException;
import com.ecole.saas.communication.domain.DeliveryEvent;
import com.ecole.saas.communication.domain.MessageDelivery;
import com.ecole.saas.communication.infrastructure.CommunicationCampaignRepository;
import com.ecole.saas.communication.infrastructure.DeliveryEventRepository;
import com.ecole.saas.communication.infrastructure.MessageDeliveryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de boîte de réception in-app strictement cloisonné par utilisateur authentifié ({@code sub})
 * et par établissement ({@code TenantContext} + RLS).
 */
@Service
public class InAppInboxService {

    private static final List<DeliveryStatus> UNREAD_STATUSES =
            List.of(DeliveryStatus.QUEUED, DeliveryStatus.SENT, DeliveryStatus.DELIVERED);

    private final MessageDeliveryRepository deliveryRepository;
    private final DeliveryEventRepository deliveryEventRepository;
    private final CommunicationCampaignRepository campaignRepository;
    private final CommunicationSubscriptionGuard subscriptionGuard;
    private final Clock clock;

    public InAppInboxService(
            MessageDeliveryRepository deliveryRepository,
            DeliveryEventRepository deliveryEventRepository,
            CommunicationCampaignRepository campaignRepository,
            CommunicationSubscriptionGuard subscriptionGuard,
            Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryEventRepository = deliveryEventRepository;
        this.campaignRepository = campaignRepository;
        this.subscriptionGuard = subscriptionGuard;
        this.clock = clock;
    }

    @PreAuthorize(CommunicationRoles.INBOX_ROLES)
    @Transactional(readOnly = true)
    public Page<MessageDelivery> listMyInbox(
            UUID currentUserId,
            boolean unreadOnly,
            MessageCategory category,
            Pageable pageable) {
        subscriptionGuard.requireReadAccess();
        if (category != null && unreadOnly) {
            return deliveryRepository.findByRecipientUserIdAndChannelAndCategoryAndStatusIn(
                    currentUserId, CommunicationChannel.IN_APP, category, UNREAD_STATUSES, pageable);
        }
        if (category != null) {
            return deliveryRepository.findByRecipientUserIdAndChannelAndCategory(
                    currentUserId, CommunicationChannel.IN_APP, category, pageable);
        }
        if (unreadOnly) {
            return deliveryRepository.findByRecipientUserIdAndChannelAndStatusIn(
                    currentUserId, CommunicationChannel.IN_APP, UNREAD_STATUSES, pageable);
        }
        return deliveryRepository.findByRecipientUserIdAndChannel(
                currentUserId, CommunicationChannel.IN_APP, pageable);
    }

    @PreAuthorize(CommunicationRoles.INBOX_ROLES)
    @Transactional(readOnly = true)
    public long countMyUnread(UUID currentUserId) {
        subscriptionGuard.requireReadAccess();
        return countUnreadInternal(currentUserId);
    }

    @PreAuthorize(CommunicationRoles.INBOX_ROLES)
    @Transactional(readOnly = true)
    public MessageDelivery getMyInboxMessage(UUID currentUserId, UUID deliveryId) {
        subscriptionGuard.requireReadAccess();
        return deliveryRepository
                .findByIdAndRecipientUserIdAndChannel(deliveryId, currentUserId, CommunicationChannel.IN_APP)
                .orElseThrow(() -> new CommunicationNotFoundException("Notification in-app introuvable."));
    }

    @PreAuthorize(CommunicationRoles.INBOX_ROLES)
    @Transactional
    public MessageDelivery markAsRead(UUID currentUserId, UUID deliveryId) {
        subscriptionGuard.requireReadAccess();
        MessageDelivery delivery = deliveryRepository
                .findByIdAndRecipientUserIdAndChannel(deliveryId, currentUserId, CommunicationChannel.IN_APP)
                .orElseThrow(() -> new CommunicationNotFoundException("Notification in-app introuvable."));
        if (delivery.getStatus() == DeliveryStatus.READ) {
            return delivery;
        }
        Instant now = clock.instant();
        DeliveryStatus previous = delivery.markRead(now);
        MessageDelivery saved = deliveryRepository.save(delivery);
        appendEvent(saved, previous, DeliveryStatus.READ, "READ", "Notification marquée comme lue dans la boîte de réception.", now);
        recomputeCampaignCounters(saved.getCampaignId(), now);
        return saved;
    }

    @PreAuthorize(CommunicationRoles.INBOX_ROLES)
    @Transactional
    public int markAllAsRead(UUID currentUserId) {
        subscriptionGuard.requireReadAccess();
        List<MessageDelivery> unread = deliveryRepository.findByRecipientUserIdAndChannelAndStatusIn(
                currentUserId, CommunicationChannel.IN_APP, UNREAD_STATUSES);
        if (unread.isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        Set<UUID> touchedCampaignIds = new HashSet<>();
        int updated = 0;
        for (MessageDelivery delivery : unread) {
            if (delivery.getStatus() == DeliveryStatus.QUEUED) {
                continue;
            }
            DeliveryStatus previous = delivery.markRead(now);
            deliveryRepository.save(delivery);
            appendEvent(delivery, previous, DeliveryStatus.READ, "READ", "Lecture groupée boîte de réception.", now);
            touchedCampaignIds.add(delivery.getCampaignId());
            updated++;
        }
        for (UUID campaignId : touchedCampaignIds) {
            recomputeCampaignCounters(campaignId, now);
        }
        return updated;
    }

    long countUnreadInternal(UUID userId) {
        if (userId == null) {
            return 0L;
        }
        return deliveryRepository.countByRecipientUserIdAndChannelAndStatusIn(
                userId, CommunicationChannel.IN_APP, UNREAD_STATUSES);
    }

    private void appendEvent(
            MessageDelivery delivery,
            DeliveryStatus previous,
            DeliveryStatus next,
            String providerStatus,
            String detail,
            Instant occurredAt) {
        int nextSeq = deliveryEventRepository.countByDeliveryId(delivery.getId()) + 1;
        deliveryEventRepository.save(new DeliveryEvent(
                delivery.getId(),
                nextSeq,
                previous,
                next,
                delivery.getProviderName(),
                providerStatus,
                null,
                detail,
                occurredAt));
    }

    private void recomputeCampaignCounters(UUID campaignId, Instant now) {
        campaignRepository.findById(campaignId).ifPresent(campaign -> {
            List<MessageDelivery> deliveries = deliveryRepository.findByCampaignIdOrderByCreatedAtAsc(campaignId);
            CommunicationCampaignService.applyAggregates(campaign, deliveries, now);
            campaignRepository.save(campaign);
        });
    }
}
