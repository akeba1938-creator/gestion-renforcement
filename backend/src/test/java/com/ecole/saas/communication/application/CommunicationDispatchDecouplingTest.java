package com.ecole.saas.communication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.communication.AudienceType;
import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.DeliveryStatus;
import com.ecole.saas.communication.GuardianTargetingMode;
import com.ecole.saas.communication.MessageCategory;
import com.ecole.saas.communication.domain.CampaignOrigin;
import com.ecole.saas.communication.domain.ChannelDeliveryGateway;
import com.ecole.saas.communication.domain.CommunicationCampaign;
import com.ecole.saas.communication.domain.GatewayDispatchResult;
import com.ecole.saas.communication.infrastructure.CommunicationCampaignRepository;
import com.ecole.saas.communication.infrastructure.CommunicationGatewayRouter;
import com.ecole.saas.communication.infrastructure.DeliveryEventRepository;
import com.ecole.saas.communication.infrastructure.MessageDeliveryRepository;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CommunicationDispatchDecouplingTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldDispatchOutsideTransactionAndRestoreTenantContextWhenGatewayClearsIt() {
        CommunicationCampaignRepository campaignRepository = mock(CommunicationCampaignRepository.class);
        MessageDeliveryRepository deliveryRepository = mock(MessageDeliveryRepository.class);
        DeliveryEventRepository eventRepository = mock(DeliveryEventRepository.class);
        CommunicationDispatchPersistenceService persistenceService =
                mock(CommunicationDispatchPersistenceService.class);
        CommunicationSubscriptionGuard subscriptionGuard = mock(CommunicationSubscriptionGuard.class);
        CommunicationScopeGuard scopeGuard = mock(CommunicationScopeGuard.class);
        AuditLogPort auditLogPort = mock(AuditLogPort.class);

        TenantId tenantId = TenantId.of(UUID.randomUUID());
        UUID campaignId = UUID.randomUUID();
        UUID delivery1Id = UUID.randomUUID();
        UUID delivery2Id = UUID.randomUUID();

        AtomicBoolean transactionWasActiveInGateway = new AtomicBoolean(false);
        List<UUID> seenTenantsInGateway = new ArrayList<>();

        ChannelDeliveryGateway mutatingGateway = new ChannelDeliveryGateway() {
            @Override
            public CommunicationChannel supportedChannel() {
                return CommunicationChannel.SMS;
            }

            @Override
            public String providerName() {
                return "TEST_SMS";
            }

            @Override
            public long defaultUnitCostXof() {
                return 25L;
            }

            @Override
            public GatewayDispatchResult dispatch(GatewayDispatchRequest request) {
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                    transactionWasActiveInGateway.set(true);
                }
                seenTenantsInGateway.add(TenantContext.requireCurrent().value());
                // Simule une passerelle qui altère ou vide le ThreadLocal TenantContext
                TenantContext.clear();
                return GatewayDispatchResult.sent("TEST_SMS", "msg-" + request.deliveryId(), 25L);
            }
        };

        CommunicationGatewayRouter gatewayRouter = new CommunicationGatewayRouter(List.of(mutatingGateway));
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC);

        CommunicationCampaignService service = new CommunicationCampaignService(
                campaignRepository,
                deliveryRepository,
                eventRepository,
                gatewayRouter,
                persistenceService,
                subscriptionGuard,
                scopeGuard,
                auditLogPort,
                fixedClock);

        CommunicationCampaign preparedCampaign = mock(CommunicationCampaign.class);
        when(preparedCampaign.getId()).thenReturn(campaignId);
        when(preparedCampaign.getChannel()).thenReturn(CommunicationChannel.SMS);

        List<CommunicationDispatchPersistenceService.QueuedDeliveryWorkItem> workItems = List.of(
                new CommunicationDispatchPersistenceService.QueuedDeliveryWorkItem(
                        delivery1Id, campaignId, CommunicationChannel.SMS, "+221771110001", null, "Msg 1", 25L, 2),
                new CommunicationDispatchPersistenceService.QueuedDeliveryWorkItem(
                        delivery2Id, campaignId, CommunicationChannel.SMS, "+221771110002", null, "Msg 2", 25L, 2));

        when(persistenceService.prepareCreateCampaign(
                        eq("idem-decoupled"),
                        eq(CampaignOrigin.CAMPAIGN_MANUAL),
                        any(),
                        eq(MessageCategory.GENERAL),
                        eq(CommunicationChannel.SMS),
                        any(),
                        any(),
                        eq(AudienceType.ALL_GUARDIANS),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        any(),
                        eq(true),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(true),
                        eq(true)))
                .thenReturn(new CommunicationDispatchPersistenceService.PreparedCampaignDispatch(
                        preparedCampaign, false, workItems));

        List<UUID> seenTenantsInFinalize = new ArrayList<>();
        CommunicationCampaign finalizedCampaign = mock(CommunicationCampaign.class);
        when(persistenceService.finalizeCampaignDispatch(eq(campaignId), anyList())).thenAnswer(invocation -> {
            seenTenantsInFinalize.add(TenantContext.requireCurrent().value());
            return finalizedCampaign;
        });

        CommunicationCampaignService.CampaignDispatchView view = TenantContext.executeWithTenant(
                tenantId,
                () -> service.createCampaign(
                        "idem-decoupled",
                        "Campagne découplée",
                        MessageCategory.GENERAL,
                        CommunicationChannel.SMS,
                        null,
                        AudienceType.ALL_GUARDIANS,
                        null,
                        GuardianTargetingMode.ALL_ACTIVE,
                        null,
                        "Bonjour {{recipientName}}",
                        Map.of(),
                        false,
                        null,
                        true,
                        UUID.randomUUID()));

        assertThat(view.campaign()).isSameAs(finalizedCampaign);
        assertThat(view.idempotentReplay()).isFalse();
        assertThat(transactionWasActiveInGateway.get()).isFalse();
        assertThat(seenTenantsInGateway).containsExactly(tenantId.value(), tenantId.value());
        assertThat(seenTenantsInFinalize).containsExactly(tenantId.value());
        assertThat(TenantContext.getCurrent()).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommunicationDispatchPersistenceService.DispatchedDeliveryOutcome>> outcomesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(persistenceService).finalizeCampaignDispatch(eq(campaignId), outcomesCaptor.capture());
        assertThat(outcomesCaptor.getValue())
                .extracting(o -> o.item().nextEventSequenceNo())
                .containsExactly(2, 2);
    }

    @Test
    void shouldComputeExactCounterDeltasAcrossStatusTransitions() {
        CommunicationDispatchPersistenceService.CounterDelta delta =
                new CommunicationDispatchPersistenceService.CounterDelta();
        delta.recordTransition(DeliveryStatus.QUEUED, DeliveryStatus.SENT, 25L);
        delta.recordTransition(DeliveryStatus.QUEUED, DeliveryStatus.FAILED, 0L);
        delta.recordTransition(DeliveryStatus.SENT, DeliveryStatus.DELIVERED, 0L);
        delta.recordTransition(DeliveryStatus.DELIVERED, DeliveryStatus.READ, 0L);

        assertThat(delta.queuedDelta).isEqualTo(-2);
        assertThat(delta.sentDelta).isEqualTo(0);
        assertThat(delta.deliveredDelta).isEqualTo(0);
        assertThat(delta.failedDelta).isEqualTo(1);
        assertThat(delta.readDelta).isEqualTo(1);
        assertThat(delta.actualCostDelta).isEqualTo(25L);
    }
}
