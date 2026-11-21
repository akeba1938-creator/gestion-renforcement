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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
        int nextSeq = deliveryEventRepository.findMaxSequenceNoByDeliveryId(saved.getId()) + 1;
        deliveryEventRepository.save(new DeliveryEvent(
                saved.getId(),
                nextSeq,
                previous,
                DeliveryStatus.READ,
                saved.getProviderName(),
                "READ",
                null,
                "Notification marquée comme lue dans la boîte de réception.",
                now));
        applyReadCounterDelta(saved.getCampaignId(), previous, now);
        return saved;
    }

    @PreAuthorize(CommunicationRoles.INBOX_ROLES)
    @Transactional
    public int markAllAsRead(UUID currentUserId) {
        subscriptionGuard.requireReadAccess();
        List<MessageDelivery> unread = deliveryRepository.findByRecipientUserIdAndChannelAndStatusIn(
                currentUserId, CommunicationChannel.IN_APP, UNREAD_STATUSES);
        List<MessageDelivery> eligible = unread.stream()
                .filter(d -> d.getStatus() != DeliveryStatus.QUEUED)
                .toList();
        if (eligible.isEmpty()) {
            return 0;
        }

        List<UUID> deliveryIds = eligible.stream().map(MessageDelivery::getId).toList();
        Map<UUID, Integer> maxSeqByDeliveryId = deliveryEventRepository
                .findMaxSequenceNoByDeliveryIdIn(deliveryIds)
                .stream()
                .collect(Collectors.toMap(
                        DeliveryEventRepository.DeliveryMaxSequence::getDeliveryId,
                        DeliveryEventRepository.DeliveryMaxSequence::getMaxSequenceNo));

        Instant now = clock.instant();
        List<DeliveryEvent> eventsToSave = new ArrayList<>(eligible.size());
        Map<UUID, int[]> deltasByCampaign = new LinkedHashMap<>();

        for (MessageDelivery delivery : eligible) {
            DeliveryStatus previous = delivery.markRead(now);
            int nextSeq = maxSeqByDeliveryId.getOrDefault(delivery.getId(), 0) + 1;
            eventsToSave.add(new DeliveryEvent(
                    delivery.getId(),
                    nextSeq,
                    previous,
                    DeliveryStatus.READ,
                    delivery.getProviderName(),
                    "READ",
                    null,
                    "Lecture groupée boîte de réception.",
                    now));
            int[] deltas = deltasByCampaign.computeIfAbsent(delivery.getCampaignId(), ignored -> new int[3]);
            if (previous == DeliveryStatus.SENT) {
                deltas[0] -= 1;
            } else if (previous == DeliveryStatus.DELIVERED) {
                deltas[1] -= 1;
            }
            deltas[2] += 1;
        }

        deliveryRepository.saveAll(eligible);
        deliveryEventRepository.saveAll(eventsToSave);
        deltasByCampaign.forEach((campaignId, deltas) ->
                campaignRepository.incrementReadCounters(campaignId, deltas[0], deltas[1], deltas[2], now));
        return eligible.size();
    }

    long countUnreadInternal(UUID userId) {
        if (userId == null) {
            return 0L;
        }
        return deliveryRepository.countByRecipientUserIdAndChannelAndStatusIn(
                userId, CommunicationChannel.IN_APP, UNREAD_STATUSES);
    }

    private void applyReadCounterDelta(UUID campaignId, DeliveryStatus previousStatus, Instant now) {
        int sentDelta = previousStatus == DeliveryStatus.SENT ? -1 : 0;
        int deliveredDelta = previousStatus == DeliveryStatus.DELIVERED ? -1 : 0;
        campaignRepository.incrementReadCounters(campaignId, sentDelta, deliveredDelta, 1, now);
    }
}
