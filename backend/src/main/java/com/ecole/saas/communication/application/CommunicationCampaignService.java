package com.ecole.saas.communication.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.communication.AudienceType;
import com.ecole.saas.communication.CampaignStatus;
import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.DeliveryStatus;
import com.ecole.saas.communication.GuardianTargetingMode;
import com.ecole.saas.communication.MessageCategory;
import com.ecole.saas.communication.domain.CampaignOrigin;
import com.ecole.saas.communication.domain.ChannelDeliveryGateway;
import com.ecole.saas.communication.domain.CommunicationCampaign;
import com.ecole.saas.communication.domain.CommunicationNotFoundException;
import com.ecole.saas.communication.domain.CommunicationValidationException;
import com.ecole.saas.communication.domain.DeliveryEvent;
import com.ecole.saas.communication.domain.DeliveryFailureCode;
import com.ecole.saas.communication.domain.GatewayDispatchResult;
import com.ecole.saas.communication.domain.MessageDelivery;
import com.ecole.saas.communication.domain.SmsSegmentCalculator;
import com.ecole.saas.communication.infrastructure.CommunicationCampaignRepository;
import com.ecole.saas.communication.infrastructure.CommunicationGatewayRouter;
import com.ecole.saas.communication.infrastructure.DeliveryEventRepository;
import com.ecole.saas.communication.infrastructure.MessageDeliveryRepository;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationCampaignService {

    private final CommunicationCampaignRepository campaignRepository;
    private final MessageDeliveryRepository deliveryRepository;
    private final DeliveryEventRepository deliveryEventRepository;
    private final CommunicationGatewayRouter gatewayRouter;
    private final CommunicationDispatchPersistenceService dispatchPersistenceService;
    private final CommunicationSubscriptionGuard subscriptionGuard;
    private final CommunicationScopeGuard scopeGuard;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public CommunicationCampaignService(
            CommunicationCampaignRepository campaignRepository,
            MessageDeliveryRepository deliveryRepository,
            DeliveryEventRepository deliveryEventRepository,
            CommunicationGatewayRouter gatewayRouter,
            CommunicationDispatchPersistenceService dispatchPersistenceService,
            CommunicationSubscriptionGuard subscriptionGuard,
            CommunicationScopeGuard scopeGuard,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.campaignRepository = campaignRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryEventRepository = deliveryEventRepository;
        this.gatewayRouter = gatewayRouter;
        this.dispatchPersistenceService = dispatchPersistenceService;
        this.subscriptionGuard = subscriptionGuard;
        this.scopeGuard = scopeGuard;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @PreAuthorize(CommunicationRoles.CAMPAIGN_SEND_ROLES)
    public CampaignDispatchView createCampaign(
            String idempotencyKey,
            String title,
            MessageCategory category,
            CommunicationChannel channel,
            UUID templateId,
            AudienceType audienceType,
            UUID audienceTargetId,
            GuardianTargetingMode guardianTargetingMode,
            String subject,
            String body,
            Map<String, String> variables,
            boolean respectMarketingOptOut,
            Instant scheduledAt,
            boolean dispatchImmediately,
            UUID actorUserId) {
        TenantId tenantId = TenantContext.requireCurrent();

        // Phase 1 : transaction courte en lot (création campagne + mise en file QUEUED)
        CommunicationDispatchPersistenceService.PreparedCampaignDispatch prepared =
                TenantContext.executeWithTenant(tenantId, () -> dispatchPersistenceService.prepareCreateCampaign(
                        idempotencyKey,
                        CampaignOrigin.CAMPAIGN_MANUAL,
                        title,
                        category,
                        channel,
                        templateId,
                        null,
                        audienceType,
                        audienceTargetId,
                        guardianTargetingMode,
                        subject,
                        body,
                        variables,
                        respectMarketingOptOut,
                        scheduledAt,
                        dispatchImmediately,
                        null,
                        null,
                        null,
                        actorUserId,
                        true,
                        true));

        if (prepared.queuedItems().isEmpty()) {
            return new CampaignDispatchView(prepared.campaign(), prepared.idempotentReplay());
        }

        // Phase 2 : appels passerelle hors transaction DB avec restauration propre du TenantContext
        List<CommunicationDispatchPersistenceService.DispatchedDeliveryOutcome> outcomes =
                dispatchQueuedItemsOutsideTransaction(
                        prepared.campaign().getChannel(), tenantId, prepared.queuedItems());

        // Phase 3 : transaction courte en lot (résultats passerelle, événements déterministes et compteurs SQL)
        CommunicationCampaign completed = TenantContext.executeWithTenant(
                tenantId,
                () -> dispatchPersistenceService.finalizeCampaignDispatch(prepared.campaign().getId(), outcomes));
        return new CampaignDispatchView(completed, false);
    }

    @PreAuthorize(CommunicationRoles.CAMPAIGN_SEND_ROLES)
    public CommunicationCampaign dispatchCampaign(UUID campaignId, UUID actorUserId) {
        TenantId tenantId = TenantContext.requireCurrent();

        CommunicationDispatchPersistenceService.PreparedCampaignDispatch prepared =
                TenantContext.executeWithTenant(
                        tenantId,
                        () -> dispatchPersistenceService.prepareDispatchExistingCampaign(campaignId, actorUserId));

        if (prepared.queuedItems().isEmpty()) {
            return prepared.campaign();
        }

        List<CommunicationDispatchPersistenceService.DispatchedDeliveryOutcome> outcomes =
                dispatchQueuedItemsOutsideTransaction(
                        prepared.campaign().getChannel(), tenantId, prepared.queuedItems());

        return TenantContext.executeWithTenant(
                tenantId,
                () -> dispatchPersistenceService.finalizeCampaignDispatch(prepared.campaign().getId(), outcomes));
    }

    @PreAuthorize(CommunicationRoles.CAMPAIGN_SEND_ROLES)
    @Transactional
    public CommunicationCampaign cancelCampaign(UUID campaignId, String reason, UUID actorUserId) {
        subscriptionGuard.requireWriteAccess();
        CommunicationCampaign campaign = requireCampaignForUpdate(campaignId);
        scopeGuard.assertCanMutateCampaign(actorUserId, campaign);
        campaign.cancel(reason, clock.instant());
        CommunicationCampaign saved = campaignRepository.save(campaign);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.COMMUNICATION,
                AuditAction.COMMUNICATION_CAMPAIGN_CANCELLED,
                "COMMUNICATION_CAMPAIGN",
                saved.getId(),
                "Annulation de la campagne " + saved.getReference() + ".",
                Map.of("reference", saved.getReference(), "status", saved.getStatus().name())));
        return saved;
    }

    @PreAuthorize(CommunicationRoles.DELIVERY_ADMIN_ROLES)
    public RetryResult retryFailedDeliveries(UUID campaignId) {
        TenantId tenantId = TenantContext.requireCurrent();

        CommunicationDispatchPersistenceService.PreparedRetryDispatch prepared =
                TenantContext.executeWithTenant(
                        tenantId, () -> dispatchPersistenceService.prepareRetryFailedDeliveries(campaignId));

        if (prepared.queuedItems().isEmpty()) {
            return new RetryResult(prepared.campaign(), 0, 0);
        }

        List<CommunicationDispatchPersistenceService.DispatchedDeliveryOutcome> outcomes =
                dispatchQueuedItemsOutsideTransaction(
                        prepared.campaign().getChannel(), tenantId, prepared.queuedItems());

        return TenantContext.executeWithTenant(
                tenantId,
                () -> dispatchPersistenceService.finalizeRetryFailedDeliveries(campaignId, outcomes));
    }

    @PreAuthorize(CommunicationRoles.DELIVERY_ADMIN_ROLES)
    @Transactional
    public MessageDelivery recordDeliveryStatus(
            UUID deliveryId,
            DeliveryStatus targetStatus,
            String providerStatus,
            DeliveryFailureCode failureCode,
            String failureReason) {
        subscriptionGuard.requireWriteAccess();
        MessageDelivery delivery = deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new CommunicationNotFoundException("Remise de message introuvable."));
        Instant now = clock.instant();
        long previousCost = delivery.getChargedCostXof();

        DeliveryStatus previous;
        switch (targetStatus) {
            case SENT -> previous = delivery.markSent(
                    delivery.getProviderName(),
                    delivery.getProviderMessageId(),
                    delivery.getChargedCostXof(),
                    now);
            case DELIVERED -> previous = delivery.markDelivered(
                    delivery.getProviderName(),
                    delivery.getProviderMessageId(),
                    delivery.getChargedCostXof(),
                    now);
            case FAILED -> {
                DeliveryFailureCode resolvedFailure = failureCode != null
                        ? failureCode
                        : DeliveryFailureCode.GATEWAY_ERROR;
                previous = delivery.markFailed(
                        delivery.getProviderName(),
                        resolvedFailure,
                        failureReason != null && !failureReason.isBlank()
                                ? failureReason
                                : "Échec signalé par la passerelle.",
                        false,
                        now);
            }
            case READ -> previous = delivery.markRead(now);
            default -> throw new CommunicationValidationException(
                    "Statut cible non pris en charge pour la mise à jour manuelle : " + targetStatus + ".");
        }

        MessageDelivery saved = deliveryRepository.save(delivery);
        if (previous != saved.getStatus()) {
            int nextSeq = deliveryEventRepository.findMaxSequenceNoByDeliveryId(saved.getId()) + 1;
            deliveryEventRepository.save(new DeliveryEvent(
                    saved.getId(),
                    nextSeq,
                    previous,
                    saved.getStatus(),
                    saved.getProviderName(),
                    providerStatus != null && !providerStatus.isBlank() ? providerStatus : saved.getStatus().name(),
                    saved.getFailureCode(),
                    saved.getFailureReason() != null ? saved.getFailureReason() : "Mise à jour du statut de remise.",
                    now));

            CommunicationDispatchPersistenceService.CounterDelta delta =
                    new CommunicationDispatchPersistenceService.CounterDelta();
            delta.recordTransition(previous, saved.getStatus(), saved.getChargedCostXof() - previousCost);
            requireCampaignForUpdate(saved.getCampaignId());
            campaignRepository.applyCounterDeltasAndRefreshStatus(
                    saved.getCampaignId(),
                    delta.queuedDelta,
                    delta.sentDelta,
                    delta.deliveredDelta,
                    delta.failedDelta,
                    delta.readDelta,
                    delta.actualCostDelta,
                    now);

            auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                    AuditCategory.COMMUNICATION,
                    AuditAction.COMMUNICATION_DELIVERY_STATUS_UPDATED,
                    "MESSAGE_DELIVERY",
                    saved.getId(),
                    "Mise à jour statut remise : " + previous + " -> " + saved.getStatus() + ".",
                    Map.of(
                            "campaignId", saved.getCampaignId().toString(),
                            "previousStatus", previous.name(),
                            "newStatus", saved.getStatus().name(),
                            "channel", saved.getChannel().name())));
        }
        return saved;
    }

    @PreAuthorize(CommunicationRoles.CAMPAIGN_READ_ROLES)
    @Transactional(readOnly = true)
    public Page<CommunicationCampaign> listCampaigns(
            CampaignStatus status,
            CommunicationChannel channel,
            MessageCategory category,
            UUID actorUserId,
            Pageable pageable) {
        subscriptionGuard.requireReadAccess();
        boolean canFinancial = scopeGuard.canAccessFinancialCampaigns();
        boolean canNonFinancial = scopeGuard.canAccessNonFinancialCampaigns();
        boolean hasTeacherScope = scopeGuard.hasTeacherOrTutorScope();

        Specification<CommunicationCampaign> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (channel != null) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (canFinancial && canNonFinancial) {
                // Accès complet aux campagnes financières et non financières (ex. direction ou cumul CASHIER + CENSOR)
            } else if (canFinancial) {
                Predicate financialPredicate = root.get("category").in(
                        MessageCategory.PAYMENT_RECEIPT, MessageCategory.PAYMENT_DUE_REMINDER);
                if (hasTeacherScope && actorUserId != null) {
                    predicates.add(cb.or(financialPredicate, cb.equal(root.get("createdBy"), actorUserId)));
                } else {
                    predicates.add(financialPredicate);
                }
            } else if (canNonFinancial) {
                predicates.add(cb.not(root.get("category").in(
                        MessageCategory.PAYMENT_RECEIPT, MessageCategory.PAYMENT_DUE_REMINDER)));
            } else if (hasTeacherScope) {
                predicates.add(cb.equal(root.get("createdBy"), actorUserId));
            } else {
                predicates.add(cb.disjunction());
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return campaignRepository.findAll(spec, pageable);
    }

    @PreAuthorize(CommunicationRoles.CAMPAIGN_READ_ROLES)
    @Transactional(readOnly = true)
    public CommunicationCampaign getCampaign(UUID campaignId, UUID actorUserId) {
        subscriptionGuard.requireReadAccess();
        CommunicationCampaign campaign = requireCampaign(campaignId);
        scopeGuard.assertCanReadCampaign(actorUserId, campaign);
        return campaign;
    }

    @PreAuthorize(CommunicationRoles.CAMPAIGN_READ_ROLES)
    @Transactional(readOnly = true)
    public Page<MessageDelivery> listCampaignDeliveries(
            UUID campaignId,
            DeliveryStatus status,
            UUID actorUserId,
            Pageable pageable) {
        subscriptionGuard.requireReadAccess();
        CommunicationCampaign campaign = requireCampaign(campaignId);
        scopeGuard.assertCanReadCampaign(actorUserId, campaign);
        if (status != null) {
            return deliveryRepository.findByCampaignIdAndStatus(campaignId, status, pageable);
        }
        return deliveryRepository.findByCampaignId(campaignId, pageable);
    }

    @PreAuthorize(CommunicationRoles.CAMPAIGN_READ_ROLES)
    @Transactional(readOnly = true)
    public MessageDelivery getDelivery(UUID deliveryId, UUID actorUserId) {
        subscriptionGuard.requireReadAccess();
        MessageDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new CommunicationNotFoundException("Remise de message introuvable."));
        CommunicationCampaign campaign = requireCampaign(delivery.getCampaignId());
        scopeGuard.assertCanReadCampaign(actorUserId, campaign);
        return delivery;
    }

    @PreAuthorize(CommunicationRoles.CAMPAIGN_READ_ROLES)
    @Transactional(readOnly = true)
    public List<DeliveryEvent> getDeliveryEvents(UUID deliveryId, UUID actorUserId) {
        subscriptionGuard.requireReadAccess();
        getDelivery(deliveryId, actorUserId);
        return deliveryEventRepository.findByDeliveryIdOrderBySequenceNoAsc(deliveryId);
    }

    @PreAuthorize(CommunicationRoles.DELIVERY_ADMIN_ROLES)
    @Transactional(readOnly = true)
    public CommunicationSummaryView getSummary() {
        subscriptionGuard.requireReadAccess();
        CommunicationCampaignRepository.CampaignTotalsSummary totals = campaignRepository.summarizeTotals();
        long totalCampaigns = totals != null ? totals.getTotalCampaigns() : 0L;
        long totalEstimatedXof = totals != null ? totals.getTotalEstimatedCostXof() : 0L;
        long totalActualXof = totals != null ? totals.getTotalActualCostXof() : 0L;

        Map<DeliveryStatus, Long> byStatus = new EnumMap<>(DeliveryStatus.class);
        for (DeliveryStatus st : DeliveryStatus.values()) {
            byStatus.put(st, 0L);
        }
        long totalDeliveries = 0L;
        for (MessageDeliveryRepository.DeliveryStatusCount row : deliveryRepository.countGroupedByStatus()) {
            if (row.getStatus() != null) {
                byStatus.put(row.getStatus(), row.getDeliveryCount());
                totalDeliveries = SmsSegmentCalculator.safeAdd(totalDeliveries, row.getDeliveryCount());
            }
        }

        Map<CommunicationChannel, Long> byChannel = new EnumMap<>(CommunicationChannel.class);
        for (CommunicationChannel ch : CommunicationChannel.values()) {
            byChannel.put(ch, 0L);
        }
        for (MessageDeliveryRepository.DeliveryChannelCount row : deliveryRepository.countGroupedByChannel()) {
            if (row.getChannel() != null) {
                byChannel.put(row.getChannel(), row.getDeliveryCount());
            }
        }

        return new CommunicationSummaryView(
                totalCampaigns,
                totalDeliveries,
                byStatus,
                byChannel,
                totalEstimatedXof,
                totalActualXof);
    }

    /**
     * Méthode interne utilisée par {@link CommunicationPortService} pour les notifications
     * déclenchées par les autres modules métier (sans court-circuiter l'idempotence ni la RLS,
     * et sans maintenir de transaction DB ouverte pendant l'appel passerelle).
     */
    CampaignDispatchView createAndDispatchSystemNotification(
            String idempotencyKey,
            String title,
            MessageCategory category,
            CommunicationChannel channel,
            String templateCode,
            AudienceType audienceType,
            UUID audienceTargetId,
            GuardianTargetingMode guardianTargetingMode,
            String fallbackSubject,
            String fallbackBody,
            Map<String, String> variables,
            boolean respectMarketingOptOut,
            String sourceModule,
            String sourceResourceType,
            UUID sourceResourceId) {
        TenantId tenantId = TenantContext.requireCurrent();

        CommunicationDispatchPersistenceService.PreparedCampaignDispatch prepared =
                TenantContext.executeWithTenant(tenantId, () -> dispatchPersistenceService.prepareCreateCampaign(
                        idempotencyKey,
                        CampaignOrigin.SYSTEM_TRIGGER,
                        title,
                        category,
                        channel,
                        null,
                        templateCode,
                        audienceType,
                        audienceTargetId,
                        guardianTargetingMode,
                        fallbackSubject,
                        fallbackBody,
                        variables,
                        respectMarketingOptOut,
                        null,
                        true,
                        sourceModule,
                        sourceResourceType,
                        sourceResourceId,
                        null,
                        false,
                        false));

        if (prepared.queuedItems().isEmpty()) {
            return new CampaignDispatchView(prepared.campaign(), prepared.idempotentReplay());
        }

        List<CommunicationDispatchPersistenceService.DispatchedDeliveryOutcome> outcomes =
                dispatchQueuedItemsOutsideTransaction(
                        prepared.campaign().getChannel(), tenantId, prepared.queuedItems());

        CommunicationCampaign completed = TenantContext.executeWithTenant(
                tenantId,
                () -> dispatchPersistenceService.finalizeCampaignDispatch(prepared.campaign().getId(), outcomes));
        return new CampaignDispatchView(completed, false);
    }

    @Transactional(readOnly = true)
    Optional<CommunicationCampaign> findByIdInternal(UUID campaignId) {
        if (campaignId == null) {
            return Optional.empty();
        }
        return campaignRepository.findById(campaignId);
    }

    private List<CommunicationDispatchPersistenceService.DispatchedDeliveryOutcome>
            dispatchQueuedItemsOutsideTransaction(
                    CommunicationChannel channel,
                    TenantId tenantId,
                    List<CommunicationDispatchPersistenceService.QueuedDeliveryWorkItem> items) {
        ChannelDeliveryGateway gateway = gatewayRouter.requireGateway(channel);
        List<CommunicationDispatchPersistenceService.DispatchedDeliveryOutcome> outcomes =
                new ArrayList<>(items.size());
        for (CommunicationDispatchPersistenceService.QueuedDeliveryWorkItem item : items) {
            GatewayDispatchResult result = TenantContext.executeWithTenant(
                    tenantId, () -> dispatchViaGateway(gateway, tenantId.value(), item));
            outcomes.add(new CommunicationDispatchPersistenceService.DispatchedDeliveryOutcome(
                    item, result, clock.instant()));
        }
        return outcomes;
    }

    private GatewayDispatchResult dispatchViaGateway(
            ChannelDeliveryGateway gateway,
            UUID tenantId,
            CommunicationDispatchPersistenceService.QueuedDeliveryWorkItem item) {
        try {
            return gateway.dispatch(new ChannelDeliveryGateway.GatewayDispatchRequest(
                    tenantId,
                    item.campaignId(),
                    item.deliveryId(),
                    item.channel(),
                    item.destinationAddress(),
                    item.renderedSubject(),
                    item.renderedBody(),
                    item.unitCostXof()));
        } catch (RuntimeException ex) {
            return GatewayDispatchResult.failed(
                    gateway.providerName(),
                    DeliveryFailureCode.GATEWAY_ERROR,
                    "Erreur technique de la passerelle lors de l'envoi.");
        }
    }

    private CommunicationCampaign requireCampaign(UUID campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CommunicationNotFoundException("Campagne de communication introuvable."));
    }

    private CommunicationCampaign requireCampaignForUpdate(UUID campaignId) {
        return campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new CommunicationNotFoundException("Campagne de communication introuvable."));
    }

    public record CampaignDispatchView(CommunicationCampaign campaign, boolean idempotentReplay) {
    }

    public record RetryResult(CommunicationCampaign campaign, int retriedCount, int succeededCount) {
    }

    public record CommunicationSummaryView(
            long totalCampaigns,
            long totalDeliveries,
            Map<DeliveryStatus, Long> deliveriesByStatus,
            Map<CommunicationChannel, Long> deliveriesByChannel,
            long totalEstimatedCostXof,
            long totalActualCostXof) {
    }
}
