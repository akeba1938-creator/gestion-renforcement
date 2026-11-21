package com.ecole.saas.communication.infrastructure;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.domain.ChannelDeliveryGateway;
import com.ecole.saas.communication.domain.DeliveryFailureCode;
import com.ecole.saas.communication.domain.GatewayDispatchResult;
import com.ecole.saas.communication.domain.SmsSegmentCalculator;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adaptateur SMS local sans appel réseau externe (remplaçable par un adaptateur opérateur en production).
 */
@Component
public class LocalStubSmsGateway implements ChannelDeliveryGateway {

    private static final Logger log = LoggerFactory.getLogger(LocalStubSmsGateway.class);

    private final CommunicationGatewayProperties properties;

    public LocalStubSmsGateway(CommunicationGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public CommunicationChannel supportedChannel() {
        return CommunicationChannel.SMS;
    }

    @Override
    public String providerName() {
        return properties.sms().providerName();
    }

    @Override
    public long defaultUnitCostXof() {
        return properties.sms().unitCostXof();
    }

    @Override
    public GatewayDispatchResult dispatch(GatewayDispatchRequest request) {
        if (request.destinationAddress() == null || !request.destinationAddress().startsWith("+")) {
            return GatewayDispatchResult.failed(
                    providerName(),
                    DeliveryFailureCode.GATEWAY_REJECTED,
                    "Numéro de téléphone incompatible avec la passerelle SMS.");
        }
        long costXof = request.estimatedCostXof() > 0
                ? request.estimatedCostXof()
                : SmsSegmentCalculator.computeDeliveryCostXof(
                        CommunicationChannel.SMS, request.body(), defaultUnitCostXof());
        String providerMessageId = "sms-" + UUID.randomUUID();
        log.debug(
                "SMS accepté par adaptateur local (tenantId={}, deliveryId={}, provider={}, costXof={})",
                request.tenantId(),
                request.deliveryId(),
                providerName(),
                costXof);
        return GatewayDispatchResult.sent(providerName(), providerMessageId, costXof);
    }
}
