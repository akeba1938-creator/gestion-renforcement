package com.ecole.saas.communication.infrastructure;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.domain.ChannelDeliveryGateway;
import com.ecole.saas.communication.domain.DeliveryFailureCode;
import com.ecole.saas.communication.domain.GatewayDispatchResult;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adaptateur e-mail local sans appel réseau externe.
 */
@Component
public class LocalStubEmailGateway implements ChannelDeliveryGateway {

    private static final Logger log = LoggerFactory.getLogger(LocalStubEmailGateway.class);

    private final CommunicationGatewayProperties properties;

    public LocalStubEmailGateway(CommunicationGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public CommunicationChannel supportedChannel() {
        return CommunicationChannel.EMAIL;
    }

    @Override
    public String providerName() {
        return properties.email().providerName();
    }

    @Override
    public long defaultUnitCostXof() {
        return properties.email().unitCostXof();
    }

    @Override
    public GatewayDispatchResult dispatch(GatewayDispatchRequest request) {
        if (request.destinationAddress() == null || !request.destinationAddress().contains("@")) {
            return GatewayDispatchResult.failed(
                    providerName(),
                    DeliveryFailureCode.GATEWAY_REJECTED,
                    "Adresse e-mail incompatible avec la passerelle.");
        }
        long costXof = request.estimatedCostXof() > 0 ? request.estimatedCostXof() : defaultUnitCostXof();
        String providerMessageId = "mail-" + UUID.randomUUID();
        log.debug(
                "E-mail accepté par adaptateur local (tenantId={}, deliveryId={}, provider={})",
                request.tenantId(),
                request.deliveryId(),
                providerName());
        return GatewayDispatchResult.sent(providerName(), providerMessageId, costXof);
    }
}
