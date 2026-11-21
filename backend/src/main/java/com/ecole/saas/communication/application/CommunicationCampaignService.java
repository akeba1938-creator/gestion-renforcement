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
import com.ecole.saas.communication.domain.CommunicationConflictException;
import com.ecole.saas.communication.domain.CommunicationHashSupport;
import com.ecole.saas.communication.domain.CommunicationNotFoundException;
import com.ecole.saas.communication.domain.CommunicationReferenceFormatter;
import com.ecole.saas.communication.domain.CommunicationValidationException;
import com.ecole.saas.communication.domain.ConsentPolicy;
import com.ecole.saas.communication.domain.ContactPreference;
import com.ecole.saas.communication.domain.DeliveryEvent;
import com.ecole.saas.communication.domain.DeliveryFailureCode;
import com.ecole.saas.communication.domain.GatewayDispatchResult;
import com.ecole.saas.communication.domain.MessageDelivery;
import com.ecole.saas.communication.domain.MessageTemplate;
import com.ecole.saas.communication.domain.RecipientType;
import com.ecole.saas.communication.domain.SmsSegmentCalculator;
import com.ecole.saas.communication.domain.TemplateRenderer;
import com.ecole.saas.communication.infrastructure.CommunicationCampaignRepository;
import com.ecole.saas.communication.infrastructure.CommunicationGatewayRouter;
import com.ecole.saas.communication.infrastructure.CommunicationSequenceRepository;
import com.ecole.saas.communication.infrastructure.ContactPreferenceRepository;
import com.ecole.saas.communication.infrastructure.DeliveryEventRepository;
import com.ecole.saas.communication.infrastructure.MessageDeliveryRepository;
import com.ecole.saas.communication.infrastructure.MessageTemplateRepository;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.shared.domain.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationCampaignService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Dakar");

    private final CommunicationCampaignRepository campaignRepository;
    private final MessageDeliveryRepository deliveryRepository;
    private final DeliveryEventRepository deliveryEventRepository;
    private final MessageTemplateRepository templateRepository;
    private final ContactPreferenceRepository preferenceRepository;
    private final CommunicationSequenceRepository sequenceRepository;
    private final AudienceResolver audienceResolver;
    private final CommunicationGatewayRouter gatewayRouter;
    private final CommunicationSubscriptionGuard subscriptionGuard;
    private final CommunicationScopeGuard scopeGuard;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public CommunicationCampaignService(
            CommunicationCampaignRepository campaignRepository,
            MessageDeliveryRepository deliveryRepository,
            DeliveryEventRepository deliveryEventRepository,
            MessageTemplateRepository templateRepository,
            ContactPreferenceRepository preferenceRepository,
            CommunicationSequenceRepository sequenceRepository,
            AudienceResolver audienceResolver,
            CommunicationGatewayRouter gatewayRouter,
            CommunicationSubscriptionGuard subscriptionGuard,
            CommunicationScopeGuard scopeGuard,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.campaignRepository = campaignRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryEventRepository = deliveryEventRepository;
        this.templateRepository = templateRepository;
        this.preferenceRepository = preferenceRepository;
        this.sequenceRepository = sequenceRepository;
        this.audienceResolver = audienceResolver;
        this.gatewayRouter = gatewayRouter;
        this.subscriptionGuard = subscriptionGuard;
        this.scopeGuard = scopeGuard;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @PreAuthorize(CommunicationRoles.CAMPAIGN_SEND_ROLES)
    @Transactional
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
        subscriptionGuard.requireWriteAccess();
        scopeGuard.assertCanSendCampaign(actorUserId, category, audienceType, audienceTargetId);

        if (scheduledAt != null && dispatchImmediately) {
            throw new CommunicationValidationException(
                    "Une campagne ne peut pas être à la fois planifiée (scheduledAt) et envoyée immédiatement.");
        }

        return createCampaignCore(
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
                true);
    }

    @PreAuthorize(CommunicationRoles.CAMPAIGN_SEND_ROLES)
    @Transactional
    public CommunicationCampaign dispatchCampaign(UUID campaignId, UUID actorUserId) {
        subscriptionGuard.requireWriteAccess();
        CommunicationCampaign campaign = requireCampaign(campaignId);
        scopeGuard.assertCanMutateCampaign(actorUserId, campaign);
        SchoolProfileSnapshot school = subscriptionGuard.requireSchoolProfile();
        return executeDispatch(campaign, school, Map.of());
    }

    @PreAuthorize(CommunicationRoles.CAMPAIGN_SEND_ROLES)
    @Transactional
    public CommunicationCampaign cancelCampaign(UUID campaignId, String reason, UUID actorUserId) {
        subscriptionGuard.requireWriteAccess();
        CommunicationCampaign campaign = requireCampaign(campaignId);
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
    @Transactional
    public RetryResult retryFailedDeliveries(UUID campaignId) {
        subscriptionGuard.requireWriteAccess();
        CommunicationCampaign campaign = requireCampaign(campaignId);
        List<MessageDelivery> deliveries = deliveryRepository.findByCampaignIdOrderByCreatedAtAsc(campaignId);
        ChannelDeliveryGateway gateway = gatewayRouter.requireGateway(campaign.getChannel());
        UUID tenantId = TenantContext.requireCurrent().value();
        Instant now = clock.instant();

        int retriedCount = 0;
        int succeededCount = 0;
        for (MessageDelivery delivery : deliveries) {
            if (!delivery.isRetryableFailure()) {
                continue;
            }
            retriedCount++;
            DeliveryStatus prevFailed = delivery.requeueForRetry(now);
            deliveryRepository.save(delivery);
            appendDeliveryEvent(
                    delivery,
                    prevFailed,
                    DeliveryStatus.QUEUED,
                    null,
                    "REQUEUED",
                    null,
                    "Relance technique après échec passerelle.",
                    now);

            GatewayDispatchResult result = dispatchViaGateway(gateway, tenantId, delivery);
            applyGatewayResult(delivery, result, now);
            deliveryRepository.save(delivery);
            if (delivery.getStatus() == DeliveryStatus.SENT || delivery.getStatus() == DeliveryStatus.DELIVERED) {
                succeededCount++;
            }
        }

        if (retriedCount > 0) {
            applyAggregates(campaign, deliveries, now);
            campaignRepository.save(campaign);
            auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                    AuditCategory.COMMUNICATION,
                    AuditAction.COMMUNICATION_DELIVERY_RETRIED,
                    "COMMUNICATION_CAMPAIGN",
                    campaign.getId(),
                    "Relance technique sur la campagne " + campaign.getReference() + ".",
                    Map.of(
                            "reference", campaign.getReference(),
                            "retriedCount", retriedCount,
                            "succeededCount", succeededCount)));
        }
        return new RetryResult(campaign, retriedCount, succeededCount);
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
        MessageDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new CommunicationNotFoundException("Remise de message introuvable."));
        Instant now = clock.instant();

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
            appendDeliveryEvent(
                    saved,
                    previous,
                    saved.getStatus(),
                    saved.getProviderName(),
                    providerStatus != null && !providerStatus.isBlank() ? providerStatus : saved.getStatus().name(),
                    saved.getFailureCode(),
                    saved.getFailureReason() != null ? saved.getFailureReason() : "Mise à jour du statut de remise.",
                    now);
            CommunicationCampaign campaign = requireCampaign(saved.getCampaignId());
            List<MessageDelivery> all = deliveryRepository.findByCampaignIdOrderByCreatedAtAsc(campaign.getId());
            applyAggregates(campaign, all, now);
            campaignRepository.save(campaign);

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
        List<CommunicationCampaign> allCampaigns = campaignRepository.findAll();
        long totalEstimatedXof = 0L;
        long totalActualXof = 0L;
        for (CommunicationCampaign c : allCampaigns) {
            totalEstimatedXof = SmsSegmentCalculator.safeAdd(totalEstimatedXof, c.getEstimatedCostXof());
            totalActualXof = SmsSegmentCalculator.safeAdd(totalActualXof, c.getActualCostXof());
        }
        Map<DeliveryStatus, Long> byStatus = new EnumMap<>(DeliveryStatus.class);
        for (DeliveryStatus st : DeliveryStatus.values()) {
            byStatus.put(st, deliveryRepository.countByStatus(st));
        }
        Map<CommunicationChannel, Long> byChannel = new EnumMap<>(CommunicationChannel.class);
        for (CommunicationChannel ch : CommunicationChannel.values()) {
            byChannel.put(ch, deliveryRepository.countByChannel(ch));
        }
        return new CommunicationSummaryView(
                allCampaigns.size(),
                deliveryRepository.count(),
                byStatus,
                byChannel,
                totalEstimatedXof,
                totalActualXof);
    }

    /**
     * Méthode interne utilisée par {@link CommunicationPortService} pour les notifications
     * déclenchées par les autres modules métier (sans court-circuiter l'idempotence ni la RLS).
     */
    @Transactional
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
        subscriptionGuard.requireWriteAccess();
        return createCampaignCore(
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
                false);
    }

    @Transactional(readOnly = true)
    Optional<CommunicationCampaign> findByIdInternal(UUID campaignId) {
        if (campaignId == null) {
            return Optional.empty();
        }
        return campaignRepository.findById(campaignId);
    }

    private CampaignDispatchView createCampaignCore(
            String rawIdempotencyKey,
            CampaignOrigin origin,
            String title,
            MessageCategory category,
            CommunicationChannel channel,
            UUID templateId,
            String templateCode,
            AudienceType audienceType,
            UUID audienceTargetId,
            GuardianTargetingMode guardianTargetingMode,
            String rawSubject,
            String rawBody,
            Map<String, String> rawVariables,
            boolean respectMarketingOptOut,
            Instant scheduledAt,
            boolean dispatchImmediately,
            String sourceModule,
            String sourceResourceType,
            UUID sourceResourceId,
            UUID actorUserId,
            boolean enforceReadScopeOnReplay) {
        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        Map<String, String> variables = rawVariables != null ? rawVariables : Map.of();

        String requestHash = computeRequestHash(
                origin,
                title,
                category,
                channel,
                templateId,
                templateCode,
                audienceType,
                audienceTargetId,
                guardianTargetingMode,
                rawSubject,
                rawBody,
                variables,
                respectMarketingOptOut,
                scheduledAt,
                dispatchImmediately);

        Optional<CommunicationCampaign> existing = campaignRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            CommunicationCampaign found = existing.get();
            if (!found.getRequestHash().equals(requestHash)) {
                throw new CommunicationConflictException(
                        "idempotency-key-conflict",
                        "Cette clé d'idempotence a déjà été utilisée avec un contenu différent.");
            }
            if (enforceReadScopeOnReplay) {
                scopeGuard.assertCanReadCampaign(actorUserId, found);
            }
            return new CampaignDispatchView(found, true);
        }

        MessageTemplate template = resolveTemplate(templateId, templateCode, channel);
        CommunicationChannel effectiveChannel = channel != null
                ? channel
                : (template != null ? template.getChannel() : null);
        if (effectiveChannel == null) {
            throw new CommunicationValidationException("Le canal de diffusion est obligatoire.");
        }

        SchoolProfileSnapshot school = subscriptionGuard.requireSchoolProfile();
        Map<String, String> baseVars = new LinkedHashMap<>();
        baseVars.put("schoolName", school.name());
        baseVars.putAll(variables);

        String resolvedSubject;
        String resolvedBody;
        if (template != null) {
            resolvedSubject = rawSubject != null && !rawSubject.isBlank()
                    ? TemplateRenderer.renderPreservingUnknown(rawSubject, baseVars)
                    : TemplateRenderer.renderPreservingUnknown(template.getSubjectTemplate(), baseVars);
            resolvedBody = rawBody != null && !rawBody.isBlank()
                    ? TemplateRenderer.renderPreservingUnknown(rawBody, baseVars)
                    : TemplateRenderer.renderPreservingUnknown(template.getBodyTemplate(), baseVars);
        } else {
            resolvedSubject = TemplateRenderer.renderPreservingUnknown(rawSubject, baseVars);
            resolvedBody = TemplateRenderer.renderPreservingUnknown(rawBody, baseVars);
        }

        ChannelDeliveryGateway gateway = gatewayRouter.requireGateway(effectiveChannel);
        long unitCostXof = template != null && template.getEstimatedUnitCostXof() > 0
                ? template.getEstimatedUnitCostXof()
                : gateway.defaultUnitCostXof();

        int year = ZonedDateTime.now(clock.withZone(resolveSchoolZone(school))).getYear();
        int seq = sequenceRepository.nextSequenceValue(year);
        String reference = CommunicationReferenceFormatter.format(school.code(), year, seq);

        String effectiveTitle = (title != null && !title.isBlank())
                ? title.trim()
                : (resolvedSubject != null && !resolvedSubject.isBlank()
                        ? truncate(resolvedSubject, 150)
                        : "Envoi " + category.name() + " " + reference);

        // Valider l'existence de la cible dès la création (évite un brouillon pointant vers une ressource inexistante ou d'un autre tenant)
        List<AudienceResolver.ResolvedRecipient> resolvedRecipients =
                audienceResolver.resolve(audienceType, audienceTargetId, guardianTargetingMode);

        CommunicationCampaign campaign = new CommunicationCampaign(
                reference,
                idempotencyKey,
                requestHash,
                origin,
                category,
                effectiveChannel,
                template != null ? template.getId() : null,
                effectiveTitle,
                audienceType,
                audienceTargetId,
                guardianTargetingMode,
                resolvedSubject,
                resolvedBody,
                respectMarketingOptOut,
                scheduledAt,
                unitCostXof,
                sourceModule,
                sourceResourceType,
                sourceResourceId,
                actorUserId);

        long perRecipientEstimate = SmsSegmentCalculator.computeDeliveryCostXof(
                effectiveChannel, resolvedBody, unitCostXof);
        campaign.setEstimatedMetrics(
                resolvedRecipients.size(),
                SmsSegmentCalculator.computeTotalCostXof(perRecipientEstimate, resolvedRecipients.size()));

        CommunicationCampaign saved = campaignRepository.save(campaign);

        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.COMMUNICATION,
                origin == CampaignOrigin.SYSTEM_TRIGGER
                        ? AuditAction.COMMUNICATION_NOTIFICATION_TRIGGERED
                        : AuditAction.COMMUNICATION_CAMPAIGN_CREATED,
                "COMMUNICATION_CAMPAIGN",
                saved.getId(),
                "Création de la campagne de communication " + saved.getReference() + " (" + saved.getChannel() + ").",
                Map.of(
                        "reference", saved.getReference(),
                        "origin", saved.getOrigin().name(),
                        "category", saved.getCategory().name(),
                        "channel", saved.getChannel().name(),
                        "audienceType", saved.getAudienceType().name(),
                        "recipientCount", resolvedRecipients.size(),
                        "estimatedCostXof", saved.getEstimatedCostXof())));

        if (dispatchImmediately) {
            saved = executeDispatchWithRecipients(saved, school, resolvedRecipients, template, baseVars);
        }
        return new CampaignDispatchView(saved, false);
    }

    private CommunicationCampaign executeDispatch(
            CommunicationCampaign campaign,
            SchoolProfileSnapshot school,
            Map<String, String> extraVars) {
        MessageTemplate template = campaign.getTemplateId() != null
                ? templateRepository.findById(campaign.getTemplateId()).orElse(null)
                : null;
        List<AudienceResolver.ResolvedRecipient> recipients = audienceResolver.resolve(
                campaign.getAudienceType(),
                campaign.getAudienceTargetId(),
                campaign.getGuardianTargetingMode());
        Map<String, String> baseVars = new LinkedHashMap<>();
        baseVars.put("schoolName", school.name());
        baseVars.putAll(extraVars);
        return executeDispatchWithRecipients(campaign, school, recipients, template, baseVars);
    }

    private CommunicationCampaign executeDispatchWithRecipients(
            CommunicationCampaign campaign,
            SchoolProfileSnapshot school,
            List<AudienceResolver.ResolvedRecipient> recipients,
            MessageTemplate template,
            Map<String, String> baseVars) {
        Instant now = clock.instant();
        campaign.markSending(now);
        campaignRepository.save(campaign);

        Map<RecipientKey, ContactPreference> preferencesByKey = loadPreferencesInBatch(recipients);
        ChannelDeliveryGateway gateway = gatewayRouter.requireGateway(campaign.getChannel());
        UUID tenantId = TenantContext.requireCurrent().value();

        List<MessageDelivery> createdDeliveries = new ArrayList<>(recipients.size());
        for (AudienceResolver.ResolvedRecipient recipient : recipients) {
            ContactPreference preference = preferencesByKey.get(
                    new RecipientKey(recipient.recipientType(), recipient.recipientId()));
            ConsentPolicy.ConsentDecision decision = ConsentPolicy.evaluate(
                    campaign.getChannel(),
                    campaign.getCategory(),
                    campaign.isRespectMarketingOptOut(),
                    preference,
                    recipient.active(),
                    recipient.phone(),
                    recipient.email(),
                    recipient.recipientUserId());

            Map<String, String> recipientVars = new LinkedHashMap<>(baseVars);
            recipientVars.put("recipientName", recipient.displayName());
            recipientVars.put("schoolName", school.name());

            String renderedSubject = renderForRecipient(
                    template != null ? template.getSubjectTemplate() : null,
                    campaign.getSubject(),
                    recipientVars);
            String renderedBody = renderForRecipient(
                    template != null ? template.getBodyTemplate() : null,
                    campaign.getBody(),
                    recipientVars);

            long deliveryUnitCostXof = decision.allowed()
                    ? SmsSegmentCalculator.computeDeliveryCostXof(
                            campaign.getChannel(), renderedBody, campaign.getUnitCostXof())
                    : 0L;

            MessageDelivery delivery = new MessageDelivery(
                    campaign.getId(),
                    recipient.recipientType(),
                    recipient.recipientId(),
                    recipient.recipientUserId(),
                    recipient.relatedStudentId(),
                    recipient.displayName(),
                    campaign.getCategory(),
                    campaign.getChannel(),
                    decision.resolvedAddress(),
                    renderedSubject,
                    renderedBody,
                    deliveryUnitCostXof,
                    now);
            delivery = deliveryRepository.save(delivery);
            appendDeliveryEvent(
                    delivery,
                    null,
                    DeliveryStatus.QUEUED,
                    null,
                    "QUEUED",
                    null,
                    "Remise mise en file pour diffusion.",
                    now);

            if (!decision.allowed()) {
                DeliveryStatus prev = delivery.markFailed(
                        null, decision.failureCode(), decision.failureReason(), false, now);
                delivery = deliveryRepository.save(delivery);
                appendDeliveryEvent(
                        delivery,
                        prev,
                        DeliveryStatus.FAILED,
                        null,
                        "BLOCKED",
                        decision.failureCode(),
                        decision.failureReason(),
                        now);
            } else {
                GatewayDispatchResult result = dispatchViaGateway(gateway, tenantId, delivery);
                applyGatewayResult(delivery, result, now);
                delivery = deliveryRepository.save(delivery);
            }
            createdDeliveries.add(delivery);
        }

        applyAggregates(campaign, createdDeliveries, now);
        CommunicationCampaign completed = campaignRepository.save(campaign);

        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.COMMUNICATION,
                AuditAction.COMMUNICATION_CAMPAIGN_DISPATCHED,
                "COMMUNICATION_CAMPAIGN",
                completed.getId(),
                "Diffusion de la campagne " + completed.getReference() + " (" + completed.getStatus() + ").",
                Map.of(
                        "reference", completed.getReference(),
                        "status", completed.getStatus().name(),
                        "totalRecipients", completed.getTotalRecipients(),
                        "sentCount", completed.getSentCount(),
                        "deliveredCount", completed.getDeliveredCount(),
                        "failedCount", completed.getFailedCount(),
                        "actualCostXof", completed.getActualCostXof())));
        return completed;
    }

    private GatewayDispatchResult dispatchViaGateway(
            ChannelDeliveryGateway gateway,
            UUID tenantId,
            MessageDelivery delivery) {
        try {
            return gateway.dispatch(new ChannelDeliveryGateway.GatewayDispatchRequest(
                    tenantId,
                    delivery.getCampaignId(),
                    delivery.getId(),
                    delivery.getChannel(),
                    delivery.getDestinationAddress(),
                    delivery.getRenderedSubject(),
                    delivery.getRenderedBody(),
                    delivery.getUnitCostXof()));
        } catch (RuntimeException ex) {
            return GatewayDispatchResult.failed(
                    gateway.providerName(),
                    DeliveryFailureCode.GATEWAY_ERROR,
                    "Erreur technique de la passerelle lors de l'envoi.");
        }
    }

    private void applyGatewayResult(MessageDelivery delivery, GatewayDispatchResult result, Instant now) {
        switch (result.status()) {
            case SENT -> {
                DeliveryStatus prev = delivery.markSent(
                        result.providerName(), result.providerMessageId(), result.chargedCostXof(), now);
                appendDeliveryEvent(
                        delivery,
                        prev,
                        DeliveryStatus.SENT,
                        result.providerName(),
                        result.providerStatus(),
                        null,
                        "Message transmis à la passerelle.",
                        now);
            }
            case DELIVERED -> {
                DeliveryStatus prev = delivery.markDelivered(
                        result.providerName(), result.providerMessageId(), result.chargedCostXof(), now);
                appendDeliveryEvent(
                        delivery,
                        prev,
                        DeliveryStatus.DELIVERED,
                        result.providerName(),
                        result.providerStatus(),
                        null,
                        "Message remis au destinataire.",
                        now);
            }
            case FAILED -> {
                DeliveryFailureCode code = result.failureCode() != null
                        ? result.failureCode()
                        : DeliveryFailureCode.GATEWAY_ERROR;
                DeliveryStatus prev = delivery.markFailed(
                        result.providerName(),
                        code,
                        result.failureReason() != null ? result.failureReason() : "Échec de remise passerelle.",
                        true,
                        now);
                appendDeliveryEvent(
                        delivery,
                        prev,
                        DeliveryStatus.FAILED,
                        result.providerName(),
                        result.providerStatus(),
                        code,
                        delivery.getFailureReason(),
                        now);
            }
            default -> {
                // Reste en QUEUED
            }
        }
    }

    private void appendDeliveryEvent(
            MessageDelivery delivery,
            DeliveryStatus previous,
            DeliveryStatus next,
            String providerName,
            String providerStatus,
            DeliveryFailureCode failureCode,
            String detail,
            Instant occurredAt) {
        int nextSeq = deliveryEventRepository.countByDeliveryId(delivery.getId()) + 1;
        deliveryEventRepository.save(new DeliveryEvent(
                delivery.getId(),
                nextSeq,
                previous,
                next,
                providerName,
                providerStatus,
                failureCode,
                detail,
                occurredAt));
    }

    private Map<RecipientKey, ContactPreference> loadPreferencesInBatch(
            List<AudienceResolver.ResolvedRecipient> recipients) {
        if (recipients.isEmpty()) {
            return Map.of();
        }
        Map<RecipientType, List<UUID>> idsByType = new EnumMap<>(RecipientType.class);
        for (AudienceResolver.ResolvedRecipient r : recipients) {
            idsByType.computeIfAbsent(r.recipientType(), ignored -> new ArrayList<>()).add(r.recipientId());
        }
        Map<RecipientKey, ContactPreference> map = new HashMap<>();
        idsByType.forEach((type, ids) -> {
            for (ContactPreference pref : preferenceRepository.findByContactTypeAndContactIdIn(type, ids)) {
                map.put(new RecipientKey(pref.getContactType(), pref.getContactId()), pref);
            }
        });
        return map;
    }

    private MessageTemplate resolveTemplate(UUID templateId, String templateCode, CommunicationChannel requestedChannel) {
        MessageTemplate template = null;
        if (templateId != null) {
            template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new CommunicationNotFoundException("Modèle de message introuvable."));
        } else if (templateCode != null && !templateCode.isBlank()) {
            String normalized = MessageTemplate.normalizeCode(templateCode);
            template = templateRepository.findByCode(normalized)
                    .orElseThrow(() -> new CommunicationNotFoundException(
                            "Modèle de message introuvable pour le code " + normalized + "."));
        }
        if (template != null) {
            template.assertActive();
            if (requestedChannel != null && template.getChannel() != requestedChannel) {
                throw new CommunicationValidationException(
                        "Le canal demandé (" + requestedChannel
                                + ") ne correspond pas au canal du modèle (" + template.getChannel() + ").");
            }
        }
        return template;
    }

    private CommunicationCampaign requireCampaign(UUID campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CommunicationNotFoundException("Campagne de communication introuvable."));
    }

    private static String renderForRecipient(
            String templateRaw,
            String campaignFallback,
            Map<String, String> recipientVars) {
        String source = (campaignFallback != null && !campaignFallback.isBlank()) ? campaignFallback : templateRaw;
        if (source == null) {
            return null;
        }
        return TemplateRenderer.render(source, recipientVars, false);
    }

    static void applyAggregates(
            CommunicationCampaign campaign,
            List<MessageDelivery> deliveries,
            Instant now) {
        int queued = 0;
        int sent = 0;
        int delivered = 0;
        int failed = 0;
        int read = 0;
        long estimated = 0L;
        long actual = 0L;
        for (MessageDelivery d : deliveries) {
            switch (d.getStatus()) {
                case QUEUED -> queued++;
                case SENT -> sent++;
                case DELIVERED -> delivered++;
                case FAILED -> failed++;
                case READ -> read++;
            }
            estimated = SmsSegmentCalculator.safeAdd(estimated, d.getUnitCostXof());
            actual = SmsSegmentCalculator.safeAdd(actual, d.getChargedCostXof());
        }
        campaign.updateAggregates(
                deliveries.size(),
                queued,
                sent,
                delivered,
                failed,
                read,
                estimated,
                actual,
                now);
    }

    private static String normalizeIdempotencyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CommunicationValidationException("La clé d'idempotence (Idempotency-Key) est obligatoire.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 128) {
            throw new CommunicationValidationException("La clé d'idempotence ne peut pas dépasser 128 caractères.");
        }
        return trimmed;
    }

    private static String computeRequestHash(
            CampaignOrigin origin,
            String title,
            MessageCategory category,
            CommunicationChannel channel,
            UUID templateId,
            String templateCode,
            AudienceType audienceType,
            UUID audienceTargetId,
            GuardianTargetingMode guardianTargetingMode,
            String subject,
            String body,
            Map<String, String> variables,
            boolean respectMarketingOptOut,
            Instant scheduledAt,
            boolean dispatchImmediately) {
        TreeMap<String, String> sortedVars = new TreeMap<>(variables);
        String canonicalVars = sortedVars.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        String canonical = String.join("|",
                String.valueOf(origin),
                title != null ? title.trim() : "",
                String.valueOf(category),
                String.valueOf(channel),
                templateId != null ? templateId.toString() : "",
                templateCode != null ? templateCode.trim().toUpperCase() : "",
                String.valueOf(audienceType),
                audienceTargetId != null ? audienceTargetId.toString() : "",
                String.valueOf(guardianTargetingMode != null ? guardianTargetingMode : GuardianTargetingMode.ALL_ACTIVE),
                subject != null ? subject.trim() : "",
                body != null ? body.trim() : "",
                canonicalVars,
                Boolean.toString(respectMarketingOptOut),
                scheduledAt != null ? scheduledAt.toString() : "",
                Boolean.toString(dispatchImmediately));
        return CommunicationHashSupport.sha256Hex(canonical);
    }

    private static ZoneId resolveSchoolZone(SchoolProfileSnapshot school) {
        if (school == null || school.timezone() == null || school.timezone().isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(school.timezone());
        } catch (DateTimeException ex) {
            return DEFAULT_ZONE;
        }
    }

    private static String truncate(String raw, int max) {
        return raw.length() <= max ? raw : raw.substring(0, max);
    }

    private record RecipientKey(RecipientType type, UUID id) {
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
