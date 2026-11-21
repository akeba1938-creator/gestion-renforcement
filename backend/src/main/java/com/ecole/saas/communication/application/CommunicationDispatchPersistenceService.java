package com.ecole.saas.communication.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.communication.AudienceType;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bean applicatif dédié aux transactions courtes de persistance en lot de la diffusion
 * (phase 1 : mise en file {@code QUEUED} ; phase 3 : résultats passerelle, événements déterministes
 * et mise à jour atomique SQL des compteurs), permettant de libérer la connexion base de données
 * pendant les appels réseau vers les passerelles externes.
 */
@Service
class CommunicationDispatchPersistenceService {

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

    CommunicationDispatchPersistenceService(
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

    @Transactional
    PreparedCampaignDispatch prepareCreateCampaign(
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
            boolean enforceSendScope,
            boolean enforceReadScopeOnReplay) {
        subscriptionGuard.requireWriteAccess();
        if (enforceSendScope) {
            scopeGuard.assertCanSendCampaign(actorUserId, category, audienceType, audienceTargetId);
        }
        if (scheduledAt != null && dispatchImmediately) {
            throw new CommunicationValidationException(
                    "Une campagne ne peut pas être à la fois planifiée (scheduledAt) et envoyée immédiatement.");
        }

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
            return new PreparedCampaignDispatch(found, true, List.of());
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

        // Valider l'existence de la cible dès la création
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

        if (!dispatchImmediately) {
            return new PreparedCampaignDispatch(saved, false, List.of());
        }

        return enqueueRecipientsBatch(saved, school, resolvedRecipients, template, baseVars);
    }

    @Transactional
    PreparedCampaignDispatch prepareDispatchExistingCampaign(UUID campaignId, UUID actorUserId) {
        subscriptionGuard.requireWriteAccess();
        CommunicationCampaign campaign = requireCampaign(campaignId);
        scopeGuard.assertCanMutateCampaign(actorUserId, campaign);
        SchoolProfileSnapshot school = subscriptionGuard.requireSchoolProfile();
        MessageTemplate template = campaign.getTemplateId() != null
                ? templateRepository.findById(campaign.getTemplateId()).orElse(null)
                : null;
        List<AudienceResolver.ResolvedRecipient> recipients = audienceResolver.resolve(
                campaign.getAudienceType(),
                campaign.getAudienceTargetId(),
                campaign.getGuardianTargetingMode());
        Map<String, String> baseVars = new LinkedHashMap<>();
        baseVars.put("schoolName", school.name());
        return enqueueRecipientsBatch(campaign, school, recipients, template, baseVars);
    }

    @Transactional
    CommunicationCampaign finalizeCampaignDispatch(UUID campaignId, List<DispatchedDeliveryOutcome> outcomes) {
        Instant now = clock.instant();
        CounterDelta delta = persistGatewayOutcomes(outcomes, null);
        campaignRepository.applyCounterDeltasAndRefreshStatus(
                campaignId,
                delta.queuedDelta,
                delta.sentDelta,
                delta.deliveredDelta,
                delta.failedDelta,
                delta.readDelta,
                delta.actualCostDelta,
                now);
        CommunicationCampaign completed = requireCampaign(campaignId);
        recordDispatchedAudit(completed);
        return completed;
    }

    @Transactional
    PreparedRetryDispatch prepareRetryFailedDeliveries(UUID campaignId) {
        subscriptionGuard.requireWriteAccess();
        CommunicationCampaign campaign = requireCampaign(campaignId);
        List<MessageDelivery> failedDeliveries =
                deliveryRepository.findByCampaignIdAndStatusOrderByCreatedAtAsc(campaignId, DeliveryStatus.FAILED);
        List<MessageDelivery> retryable = failedDeliveries.stream()
                .filter(MessageDelivery::isRetryableFailure)
                .toList();
        if (retryable.isEmpty()) {
            return new PreparedRetryDispatch(campaign, List.of());
        }

        List<UUID> retryIds = retryable.stream().map(MessageDelivery::getId).toList();
        Map<UUID, Integer> maxSeqByDeliveryId = deliveryEventRepository
                .findMaxSequenceNoByDeliveryIdIn(retryIds)
                .stream()
                .collect(Collectors.toMap(
                        DeliveryEventRepository.DeliveryMaxSequence::getDeliveryId,
                        DeliveryEventRepository.DeliveryMaxSequence::getMaxSequenceNo));

        Instant now = clock.instant();
        List<DeliveryEvent> requeueEvents = new ArrayList<>(retryable.size());
        List<QueuedDeliveryWorkItem> workItems = new ArrayList<>(retryable.size());
        for (MessageDelivery delivery : retryable) {
            DeliveryStatus prevFailed = delivery.requeueForRetry(now);
            int requeueSeq = maxSeqByDeliveryId.getOrDefault(delivery.getId(), 0) + 1;
            requeueEvents.add(new DeliveryEvent(
                    delivery.getId(),
                    requeueSeq,
                    prevFailed,
                    DeliveryStatus.QUEUED,
                    null,
                    "REQUEUED",
                    null,
                    "Relance technique après échec passerelle.",
                    now));
            workItems.add(new QueuedDeliveryWorkItem(
                    delivery.getId(),
                    campaign.getId(),
                    delivery.getChannel(),
                    delivery.getDestinationAddress(),
                    delivery.getRenderedSubject(),
                    delivery.getRenderedBody(),
                    delivery.getUnitCostXof(),
                    requeueSeq + 1));
        }

        deliveryRepository.saveAll(retryable);
        deliveryEventRepository.saveAll(requeueEvents);
        campaignRepository.applyCounterDeltasAndRefreshStatus(
                campaignId,
                retryable.size(),
                0,
                0,
                -retryable.size(),
                0,
                0L,
                now);
        return new PreparedRetryDispatch(requireCampaign(campaignId), workItems);
    }

    @Transactional
    CommunicationCampaignService.RetryResult finalizeRetryFailedDeliveries(
            UUID campaignId, List<DispatchedDeliveryOutcome> outcomes) {
        Instant now = clock.instant();
        int[] succeededCounter = new int[1];
        CounterDelta delta = persistGatewayOutcomes(outcomes, succeededCounter);
        campaignRepository.applyCounterDeltasAndRefreshStatus(
                campaignId,
                delta.queuedDelta,
                delta.sentDelta,
                delta.deliveredDelta,
                delta.failedDelta,
                delta.readDelta,
                delta.actualCostDelta,
                now);
        CommunicationCampaign updated = requireCampaign(campaignId);
        int retriedCount = outcomes.size();
        int succeededCount = succeededCounter[0];
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.COMMUNICATION,
                AuditAction.COMMUNICATION_DELIVERY_RETRIED,
                "COMMUNICATION_CAMPAIGN",
                updated.getId(),
                "Relance technique sur la campagne " + updated.getReference() + ".",
                Map.of(
                        "reference", updated.getReference(),
                        "retriedCount", retriedCount,
                        "succeededCount", succeededCount)));
        return new CommunicationCampaignService.RetryResult(updated, retriedCount, succeededCount);
    }

    private PreparedCampaignDispatch enqueueRecipientsBatch(
            CommunicationCampaign campaign,
            SchoolProfileSnapshot school,
            List<AudienceResolver.ResolvedRecipient> recipients,
            MessageTemplate template,
            Map<String, String> baseVars) {
        Instant now = clock.instant();
        campaign.markSending(now);
        gatewayRouter.requireGateway(campaign.getChannel());

        Map<RecipientKey, ContactPreference> preferencesByKey = loadPreferencesInBatch(recipients);

        List<MessageDelivery> deliveriesToCreate = new ArrayList<>(recipients.size());
        List<ConsentPolicy.ConsentDecision> decisions = new ArrayList<>(recipients.size());
        long totalEstimatedCostXof = 0L;
        int blockedCount = 0;

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
            totalEstimatedCostXof = SmsSegmentCalculator.safeAdd(totalEstimatedCostXof, deliveryUnitCostXof);

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
            if (!decision.allowed()) {
                delivery.markFailed(null, decision.failureCode(), decision.failureReason(), false, now);
                blockedCount++;
            }
            deliveriesToCreate.add(delivery);
            decisions.add(decision);
        }

        List<MessageDelivery> savedDeliveries = deliveriesToCreate.isEmpty()
                ? List.of()
                : deliveryRepository.saveAll(deliveriesToCreate);

        List<DeliveryEvent> initialEvents = new ArrayList<>(savedDeliveries.size() + blockedCount);
        List<QueuedDeliveryWorkItem> queuedItems = new ArrayList<>(savedDeliveries.size() - blockedCount);

        for (int i = 0; i < savedDeliveries.size(); i++) {
            MessageDelivery delivery = savedDeliveries.get(i);
            ConsentPolicy.ConsentDecision decision = decisions.get(i);
            initialEvents.add(new DeliveryEvent(
                    delivery.getId(),
                    1,
                    null,
                    DeliveryStatus.QUEUED,
                    null,
                    "QUEUED",
                    null,
                    "Remise mise en file pour diffusion.",
                    now));
            if (!decision.allowed()) {
                initialEvents.add(new DeliveryEvent(
                        delivery.getId(),
                        2,
                        DeliveryStatus.QUEUED,
                        DeliveryStatus.FAILED,
                        null,
                        "BLOCKED",
                        decision.failureCode(),
                        decision.failureReason(),
                        now));
            } else {
                queuedItems.add(new QueuedDeliveryWorkItem(
                        delivery.getId(),
                        campaign.getId(),
                        delivery.getChannel(),
                        delivery.getDestinationAddress(),
                        delivery.getRenderedSubject(),
                        delivery.getRenderedBody(),
                        delivery.getUnitCostXof(),
                        2));
            }
        }

        if (!initialEvents.isEmpty()) {
            deliveryEventRepository.saveAll(initialEvents);
        }

        campaign.updateAggregates(
                savedDeliveries.size(),
                queuedItems.size(),
                0,
                0,
                blockedCount,
                0,
                totalEstimatedCostXof,
                0L,
                now);
        CommunicationCampaign savedCampaign = campaignRepository.save(campaign);

        if (queuedItems.isEmpty()) {
            recordDispatchedAudit(savedCampaign);
        }
        return new PreparedCampaignDispatch(savedCampaign, false, queuedItems);
    }

    private CounterDelta persistGatewayOutcomes(
            List<DispatchedDeliveryOutcome> outcomes, int[] succeededCounter) {
        CounterDelta delta = new CounterDelta();
        if (outcomes.isEmpty()) {
            return delta;
        }
        List<UUID> deliveryIds = outcomes.stream()
                .map(o -> o.item().deliveryId())
                .toList();
        Map<UUID, MessageDelivery> deliveriesById = deliveryRepository.findAllById(deliveryIds).stream()
                .collect(Collectors.toMap(MessageDelivery::getId, Function.identity()));

        List<MessageDelivery> deliveriesToSave = new ArrayList<>(outcomes.size());
        List<DeliveryEvent> eventsToSave = new ArrayList<>(outcomes.size());

        for (DispatchedDeliveryOutcome outcome : outcomes) {
            MessageDelivery delivery = deliveriesById.get(outcome.item().deliveryId());
            if (delivery == null) {
                continue;
            }
            long previousCost = delivery.getChargedCostXof();
            DeliveryStatus previousStatus = delivery.getStatus();
            DeliveryEvent event = applyGatewayResult(
                    delivery, outcome.result(), outcome.item().nextEventSequenceNo(), outcome.dispatchedAt());
            if (event != null) {
                eventsToSave.add(event);
            }
            if (delivery.getStatus() != previousStatus) {
                delta.recordTransition(
                        previousStatus,
                        delivery.getStatus(),
                        delivery.getChargedCostXof() - previousCost);
                deliveriesToSave.add(delivery);
            }
            if (succeededCounter != null
                    && (delivery.getStatus() == DeliveryStatus.SENT
                            || delivery.getStatus() == DeliveryStatus.DELIVERED)) {
                succeededCounter[0]++;
            }
        }

        if (!deliveriesToSave.isEmpty()) {
            deliveryRepository.saveAll(deliveriesToSave);
        }
        if (!eventsToSave.isEmpty()) {
            deliveryEventRepository.saveAll(eventsToSave);
        }
        return delta;
    }

    private static DeliveryEvent applyGatewayResult(
            MessageDelivery delivery,
            GatewayDispatchResult result,
            int sequenceNo,
            Instant occurredAt) {
        return switch (result.status()) {
            case SENT -> {
                DeliveryStatus prev = delivery.markSent(
                        result.providerName(), result.providerMessageId(), result.chargedCostXof(), occurredAt);
                yield new DeliveryEvent(
                        delivery.getId(),
                        sequenceNo,
                        prev,
                        DeliveryStatus.SENT,
                        result.providerName(),
                        result.providerStatus(),
                        null,
                        "Message transmis à la passerelle.",
                        occurredAt);
            }
            case DELIVERED -> {
                DeliveryStatus prev = delivery.markDelivered(
                        result.providerName(), result.providerMessageId(), result.chargedCostXof(), occurredAt);
                yield new DeliveryEvent(
                        delivery.getId(),
                        sequenceNo,
                        prev,
                        DeliveryStatus.DELIVERED,
                        result.providerName(),
                        result.providerStatus(),
                        null,
                        "Message remis au destinataire.",
                        occurredAt);
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
                        occurredAt);
                yield new DeliveryEvent(
                        delivery.getId(),
                        sequenceNo,
                        prev,
                        DeliveryStatus.FAILED,
                        result.providerName(),
                        result.providerStatus(),
                        code,
                        delivery.getFailureReason(),
                        occurredAt);
            }
            default -> null;
        };
    }

    private void recordDispatchedAudit(CommunicationCampaign completed) {
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

    private MessageTemplate resolveTemplate(
            UUID templateId, String templateCode, CommunicationChannel requestedChannel) {
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

    static final class CounterDelta {
        int queuedDelta;
        int sentDelta;
        int deliveredDelta;
        int failedDelta;
        int readDelta;
        long actualCostDelta;

        void recordTransition(DeliveryStatus previous, DeliveryStatus next, long costDelta) {
            if (previous != null) {
                addStatus(previous, -1);
            }
            if (next != null) {
                addStatus(next, 1);
            }
            actualCostDelta += costDelta;
        }

        private void addStatus(DeliveryStatus status, int delta) {
            switch (status) {
                case QUEUED -> queuedDelta += delta;
                case SENT -> sentDelta += delta;
                case DELIVERED -> deliveredDelta += delta;
                case FAILED -> failedDelta += delta;
                case READ -> readDelta += delta;
            }
        }
    }

    private record RecipientKey(RecipientType type, UUID id) {
    }

    record QueuedDeliveryWorkItem(
            UUID deliveryId,
            UUID campaignId,
            CommunicationChannel channel,
            String destinationAddress,
            String renderedSubject,
            String renderedBody,
            long unitCostXof,
            int nextEventSequenceNo) {
    }

    record DispatchedDeliveryOutcome(
            QueuedDeliveryWorkItem item,
            GatewayDispatchResult result,
            Instant dispatchedAt) {
    }

    record PreparedCampaignDispatch(
            CommunicationCampaign campaign,
            boolean idempotentReplay,
            List<QueuedDeliveryWorkItem> queuedItems) {
    }

    record PreparedRetryDispatch(
            CommunicationCampaign campaign,
            List<QueuedDeliveryWorkItem> queuedItems) {
    }
}
