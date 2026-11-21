package com.ecole.saas.communication.infrastructure;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.domain.ChannelDeliveryGateway;
import com.ecole.saas.communication.domain.CommunicationValidationException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Routeur des adaptateurs {@link ChannelDeliveryGateway} par canal de diffusion.
 */
@Component
public class CommunicationGatewayRouter {

    private final Map<CommunicationChannel, ChannelDeliveryGateway> gateways =
            new EnumMap<>(CommunicationChannel.class);

    public CommunicationGatewayRouter(List<ChannelDeliveryGateway> availableGateways) {
        for (ChannelDeliveryGateway gateway : availableGateways) {
            gateways.put(gateway.supportedChannel(), gateway);
        }
    }

    public ChannelDeliveryGateway requireGateway(CommunicationChannel channel) {
        ChannelDeliveryGateway gateway = gateways.get(channel);
        if (gateway == null) {
            throw new CommunicationValidationException(
                    "Aucune passerelle configurée pour le canal " + channel + ".");
        }
        return gateway;
    }
}
