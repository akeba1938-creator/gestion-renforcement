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
 * Adaptateur WhatsApp local sans appel réseau externe.
 */
@Component
public class LocalStubWhatsAppGateway implements ChannelDeliveryGateway {

    private static final Logger log = LoggerFactory.getLogger(LocalStubWhatsAppGateway.class);

    private final CommunicationGatewayProperties properties;

    public LocalStubWhatsAppGateway(CommunicationGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public CommunicationChannel supportedChannel() {
        return CommunicationChannel.WHATSAPP;
    }

    @Override
    public String providerName() {
        return properties.whatsapp().providerName();
    }

    @Override
    public long defaultUnitCostXof() {
        return properties.whatsapp().unitCostXof();
    }

    @Override
    public GatewayDispatchResult dispatch(GatewayDispatchRequest request) {
        if (request.destinationAddress() == null || !request.destinationAddress().startsWith("+")) {
            return GatewayDispatchResult.failed(
                    providerName(),
                    DeliveryFailureCode.GATEWAY_REJECTED,
                    "Numéro de téléphone incompatible avec la passerelle WhatsApp.");
        }
        long costXof = request.estimatedCostXof() > 0 ? request.estimatedCostXof() : defaultUnitCostXof();
        String providerMessageId = "wa-" + UUID.randomUUID();
        log.debug(
                "Message WhatsApp accepté par adaptateur local (tenantId={}, deliveryId={}, provider={}, costXof={})",
                request.tenantId(),
                request.deliveryId(),
                providerName(),
                costXof);
        return GatewayDispatchResult.sent(providerName(), providerMessageId, costXof);
    }
}
